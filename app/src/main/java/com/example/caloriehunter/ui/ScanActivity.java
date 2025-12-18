package com.example.caloriehunter.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.example.caloriehunter.BuildConfig;
import com.example.caloriehunter.R;
import com.example.caloriehunter.api.GeminiService;
import com.example.caloriehunter.data.model.Item;
import com.example.caloriehunter.data.model.Monster;
import com.example.caloriehunter.data.model.NutritionData;
import com.example.caloriehunter.data.repository.FirebaseRepository;
import com.example.caloriehunter.data.repository.FoodRepository;
import com.example.caloriehunter.databinding.ActivityScanBinding;
import com.example.caloriehunter.game.FoodAnalyzer;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 바코드 스캔 화면
 * - CameraX로 카메라 프리뷰
 * - ML Kit으로 바코드 인식
 * - Open Food Facts API로 영양 정보 조회
 */
public class ScanActivity extends AppCompatActivity {

    private static final String TAG = "ScanActivity";
    private ActivityScanBinding binding;

    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private FoodRepository foodRepository;
    private FirebaseRepository firebaseRepository;
    private FoodAnalyzer foodAnalyzer;
    private GeminiService geminiService;
    private ImageCapture imageCapture;
    private Handler mainHandler;

    private boolean isProcessing = false;
    private FoodAnalyzer.AnalysisResult lastResult;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startCamera();
                } else {
                    Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 초기화
        cameraExecutor = Executors.newSingleThreadExecutor();
        foodRepository = FoodRepository.getInstance();
        firebaseRepository = FirebaseRepository.getInstance();
        foodAnalyzer = new FoodAnalyzer();
        geminiService = GeminiService.getInstance();
        mainHandler = new Handler(Looper.getMainLooper());

        // ===== API 키 테스트 (확인 후 삭제) =====
        Log.d("API_TEST", "Gemini Key: " + BuildConfig.GEMINI_API_KEY);

        // 바코드 스캐너 옵션
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        setupClickListeners();
        checkCameraPermission();
    }

    private void setupClickListeners() {
        binding.btnClose.setOnClickListener(v -> finish());

        binding.btnManualSearch.setOnClickListener(v -> showManualSearchDialog());

        binding.btnCapturePhoto.setOnClickListener(v -> captureAndAnalyzePhoto());

        binding.btnConfirmResult.setOnClickListener(v -> {
            saveResultAndFinish();
        });
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // 프리뷰
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.cameraPreview.getSurfaceProvider());

                // 이미지 분석 (바코드)
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                // 이미지 캡처 (AI 분석용)
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                // 후면 카메라
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // 바인딩
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis, imageCapture);

            } catch (Exception e) {
                Log.e(TAG, "Camera binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @androidx.camera.core.ExperimentalGetImage
    private void analyzeImage(ImageProxy imageProxy) {
        if (isProcessing) {
            imageProxy.close();
            return;
        }

        var mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

        barcodeScanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    for (Barcode barcode : barcodes) {
                        String rawValue = barcode.getRawValue();
                        if (rawValue != null && !rawValue.isEmpty()) {
                            isProcessing = true;
                            runOnUiThread(() -> onBarcodeDetected(rawValue));
                            break;
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Barcode scanning failed", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void onBarcodeDetected(String barcode) {
        Log.d(TAG, "Barcode detected: " + barcode);
        showLoading(true);

        foodRepository.searchByBarcode(barcode, new FoodRepository.FoodCallback() {
            @Override
            public void onSuccess(NutritionData data) {
                runOnUiThread(() -> processNutritionData(data));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    showLoading(false);
                    isProcessing = false;
                    Toast.makeText(ScanActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showManualSearchDialog() {
        EditText input = new EditText(this);
        input.setHint("음식명을 입력하세요 (예: 김치찌개)");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setPadding(48, 32, 48, 32);

        new AlertDialog.Builder(this)
                .setTitle("음식 검색")
                .setView(input)
                .setPositiveButton("검색", (dialog, which) -> {
                    String foodName = input.getText().toString().trim();
                    if (!foodName.isEmpty()) {
                        searchByFoodName(foodName);
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void searchByFoodName(String foodName) {
        showLoading(true);
        isProcessing = true;

        // 식약처 API 먼저 시도, 실패하면 하드코딩 데이터 사용
        foodRepository.searchByFoodName(foodName, new FoodRepository.FoodCallback() {
            @Override
            public void onSuccess(NutritionData data) {
                runOnUiThread(() -> processNutritionData(data));
            }

            @Override
            public void onError(String message) {
                // 식약처 API 실패 시 하드코딩 데이터로 fallback
                runOnUiThread(() -> {
                    NutritionData data = getDefaultNutritionData(foodName);
                    processNutritionData(data);
                });
            }
        });
    }

    // 기본 음식 영양 데이터 (임시)
    private NutritionData getDefaultNutritionData(String foodName) {
        NutritionData data = new NutritionData();
        data.setFoodName(foodName);
        data.setSource("기본 데이터");
        data.setConfidence(0.6f);

        String lower = foodName.toLowerCase();

        // 흔한 음식들의 대략적인 영양 정보
        // 불건강한 음식 → 몬스터 (당류 15g+, 포화지방 5g+, 나트륨 600mg+ 기준)
        if (lower.contains("피자")) {
            data.setCalories(700); data.setProtein(25); data.setSugar(8);
            data.setSodium(1500); data.setSaturatedFat(14); data.setFiber(3);
            data.setTransFat(1.0f);
        } else if (lower.contains("치킨") || lower.contains("후라이드") || lower.contains("양념")) {
            data.setCalories(450); data.setProtein(30); data.setSugar(8);
            data.setSodium(1200); data.setSaturatedFat(12); data.setFiber(0);
            data.setTransFat(1.5f);
        } else if (lower.contains("햄버거") || lower.contains("버거")) {
            data.setCalories(550); data.setProtein(25); data.setSugar(9);
            data.setSodium(1000); data.setSaturatedFat(11); data.setFiber(2);
            data.setTransFat(1.2f);
        } else if (lower.contains("라면")) {
            data.setCalories(500); data.setProtein(10); data.setSugar(4);
            data.setSodium(1800); data.setSaturatedFat(8); data.setFiber(2);
            data.setTransFat(0.8f);
        } else if (lower.contains("감자튀김") || lower.contains("프렌치프라이") || lower.contains("프라이")) {
            data.setCalories(450); data.setProtein(4); data.setSugar(0);
            data.setSodium(700); data.setSaturatedFat(7); data.setFiber(3);
            data.setTransFat(3.0f);
        } else if (lower.contains("도넛") || lower.contains("donut")) {
            data.setCalories(450); data.setProtein(5); data.setSugar(25);
            data.setSodium(400); data.setSaturatedFat(10); data.setFiber(1);
            data.setTransFat(1.5f);
        } else if (lower.contains("케이크") || lower.contains("cake")) {
            data.setCalories(400); data.setProtein(5); data.setSugar(35);
            data.setSodium(300); data.setSaturatedFat(8); data.setFiber(1);
        } else if (lower.contains("아이스크림") || lower.contains("ice cream")) {
            data.setCalories(270); data.setProtein(5); data.setSugar(28);
            data.setSodium(100); data.setSaturatedFat(9); data.setFiber(0);
        } else if (lower.contains("콜라") || lower.contains("사이다") || lower.contains("탄산")) {
            data.setCalories(140); data.setProtein(0); data.setSugar(39);
            data.setSodium(45); data.setSaturatedFat(0); data.setFiber(0);
        } else if (lower.contains("삼겹살")) {
            data.setCalories(550); data.setProtein(20); data.setSugar(0);
            data.setSodium(800); data.setSaturatedFat(18); data.setFiber(0);
        // 건강한 음식 → 아이템 (고단백, 고섬유, 저당)
        } else if (lower.contains("닭가슴살") || lower.contains("chicken breast")) {
            data.setCalories(165); data.setProtein(31); data.setSugar(0);
            data.setSodium(75); data.setSaturatedFat(1); data.setFiber(0);
        } else if (lower.contains("샐러드") || lower.contains("salad")) {
            data.setCalories(50); data.setProtein(3); data.setSugar(4);
            data.setSodium(30); data.setSaturatedFat(0.1f); data.setFiber(5);
        } else if (lower.contains("고구마")) {
            data.setCalories(130); data.setProtein(2); data.setSugar(5);
            data.setSodium(40); data.setSaturatedFat(0); data.setFiber(4);
        } else if (lower.contains("바나나")) {
            data.setCalories(105); data.setProtein(1); data.setSugar(14);
            data.setSodium(1); data.setSaturatedFat(0); data.setFiber(3);
        } else if (lower.contains("계란") || lower.contains("달걀")) {
            data.setCalories(155); data.setProtein(13); data.setSugar(1);
            data.setSodium(125); data.setSaturatedFat(3); data.setFiber(0);
        } else if (lower.contains("두부") || lower.contains("tofu")) {
            data.setCalories(80); data.setProtein(8); data.setSugar(1);
            data.setSodium(10); data.setSaturatedFat(0.5f); data.setFiber(0.5f);
        } else if (lower.contains("연어") || lower.contains("salmon")) {
            data.setCalories(200); data.setProtein(25); data.setSugar(0);
            data.setSodium(60); data.setSaturatedFat(2); data.setFiber(0);
        } else if (lower.contains("브로콜리") || lower.contains("broccoli")) {
            data.setCalories(55); data.setProtein(4); data.setSugar(2);
            data.setSodium(35); data.setSaturatedFat(0); data.setFiber(5);
        } else if (lower.contains("오트밀") || lower.contains("귀리")) {
            data.setCalories(150); data.setProtein(5); data.setSugar(1);
            data.setSodium(0); data.setSaturatedFat(0.5f); data.setFiber(4);
        } else if (lower.contains("프로틴") || lower.contains("protein")) {
            data.setCalories(120); data.setProtein(24); data.setSugar(2);
            data.setSodium(150); data.setSaturatedFat(0.5f); data.setFiber(1);
        } else if (lower.contains("밥") || lower.contains("공기밥")) {
            data.setCalories(200); data.setProtein(4); data.setSugar(0);
            data.setSodium(5); data.setSaturatedFat(0.1f); data.setFiber(0.5f);
        } else if (lower.contains("김치찌개")) {
            data.setCalories(150); data.setProtein(10); data.setSugar(3);
            data.setSodium(1300); data.setSaturatedFat(3); data.setFiber(3);
        } else if (lower.contains("아메리카노") || lower.contains("커피")) {
            data.setCalories(5); data.setProtein(0.3f); data.setSugar(0);
            data.setSodium(5); data.setSaturatedFat(0); data.setFiber(0);
        } else {
            // 기본값 (알 수 없는 음식) - 약간 불건강한 쪽으로
            data.setCalories(350); data.setProtein(10); data.setSugar(12);
            data.setSodium(700); data.setSaturatedFat(6); data.setFiber(2);
        }

        return data;
    }

    /**
     * 카메라로 사진을 찍어 Gemini AI로 음식 분석
     */
    private void captureAndAnalyzePhoto() {
        if (imageCapture == null) {
            Toast.makeText(this, "카메라 초기화 중...", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);
        isProcessing = true;

        imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                Bitmap bitmap = imageProxyToBitmap(image);
                image.close();

                if (bitmap != null) {
                    // Gemini AI로 이미지 분석
                    geminiService.analyzeFoodImage(bitmap, new GeminiService.GeminiCallback() {
                        @Override
                        public void onSuccess(NutritionData nutritionData) {
                            mainHandler.post(() -> processNutritionData(nutritionData));
                        }

                        @Override
                        public void onError(String error) {
                            mainHandler.post(() -> {
                                showLoading(false);
                                isProcessing = false;
                                Toast.makeText(ScanActivity.this, "AI 분석 실패: " + error, Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        showLoading(false);
                        isProcessing = false;
                        Toast.makeText(ScanActivity.this, "이미지 변환 실패", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                mainHandler.post(() -> {
                    showLoading(false);
                    isProcessing = false;
                    Toast.makeText(ScanActivity.this, "사진 촬영 실패", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * ImageProxy를 Bitmap으로 변환
     */
    @androidx.camera.core.ExperimentalGetImage
    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            android.media.Image mediaImage = image.getImage();
            if (mediaImage == null) return null;

            android.media.Image.Plane[] planes = mediaImage.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            android.graphics.YuvImage yuvImage = new android.graphics.YuvImage(
                    nv21, android.graphics.ImageFormat.NV21,
                    mediaImage.getWidth(), mediaImage.getHeight(), null);

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            yuvImage.compressToJpeg(new android.graphics.Rect(0, 0,
                    mediaImage.getWidth(), mediaImage.getHeight()), 90, out);

            byte[] imageBytes = out.toByteArray();
            Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

            // 회전 보정
            int rotation = image.getImageInfo().getRotationDegrees();
            if (rotation != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }

            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Image conversion failed", e);
            return null;
        }
    }

    private void processNutritionData(NutritionData data) {
        showLoading(false);

        String userId = firebaseRepository.getCurrentUserId();
        if (userId == null) {
            Toast.makeText(this, "로그인이 필요합니다", Toast.LENGTH_SHORT).show();
            return;
        }

        // 음식 분석
        lastResult = foodAnalyzer.analyze(data, userId);

        // 결과 UI 표시
        showResultCard(data, lastResult);
    }

    private void showResultCard(NutritionData food, FoodAnalyzer.AnalysisResult result) {
        binding.resultCard.setVisibility(View.VISIBLE);

        if (result.isMonster()) {
            Monster monster = result.getMonster();
            binding.tvResultType.setText("🔴 몬스터 발견!");
            binding.tvResultType.setTextColor(getColor(R.color.hp_red));
            binding.tvResultName.setText(monster.getName());
            binding.tvResultFood.setText(food.getFoodName());
            binding.tvNutritionSummary.setText(
                    String.format("당류 %.0fg · 나트륨 %.0fmg · HP %d",
                            food.getSugar(), food.getSodium(), monster.getMaxHp()));
        } else {
            Item item = result.getItem();
            binding.tvResultType.setText("🟢 아이템 획득!");
            binding.tvResultType.setTextColor(getColor(R.color.hp_green));
            binding.tvResultName.setText(item.getName());
            binding.tvResultFood.setText(food.getFoodName());

            String statText;
            switch (item.getType()) {
                case WEAPON:
                    statText = String.format("공격력 +%d", item.getAttackPower());
                    break;
                case POTION:
                    statText = String.format("회복량 +%d", item.getHealAmount());
                    break;
                default:
                    statText = String.format("버프 +%d", item.getBuffPower());
            }
            binding.tvNutritionSummary.setText(
                    String.format("단백질 %.0fg · 식이섬유 %.0fg · %s",
                            food.getProtein(), food.getFiber(), statText));
        }
    }

    private void saveResultAndFinish() {
        if (lastResult == null) return;

        showLoading(true);

        if (lastResult.isMonster()) {
            firebaseRepository.saveMonster(lastResult.getMonster(), new FirebaseRepository.MonsterCallback() {
                @Override
                public void onSuccess(Monster monster) {
                    runOnUiThread(() -> {
                        Toast.makeText(ScanActivity.this, "몬스터가 나타났습니다!", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(ScanActivity.this, "저장 실패: " + message, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            firebaseRepository.saveItem(lastResult.getItem(), new FirebaseRepository.ItemCallback() {
                @Override
                public void onSuccess(Item item) {
                    runOnUiThread(() -> {
                        Toast.makeText(ScanActivity.this, "아이템을 획득했습니다!", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(ScanActivity.this, "저장 실패: " + message, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    }

    private void showLoading(boolean show) {
        binding.loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        barcodeScanner.close();
    }
}
