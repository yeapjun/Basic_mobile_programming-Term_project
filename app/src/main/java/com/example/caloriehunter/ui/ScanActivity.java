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
import com.example.caloriehunter.data.model.DailyQuest;
import com.example.caloriehunter.data.model.Item;
import com.example.caloriehunter.data.model.Monster;
import com.example.caloriehunter.data.model.NutritionData;
import com.example.caloriehunter.data.model.NutritionRecord;
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
    private NutritionData lastNutritionData;

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
                // Open Food Facts 실패 시 Gemini AI로 이미지 분석 fallback
                Log.d(TAG, "Open Food Facts 실패, Gemini AI로 시도: " + message);
                runOnUiThread(() -> {
                    Toast.makeText(ScanActivity.this,
                            "제품 DB에 없음. AI 분석 중...", Toast.LENGTH_SHORT).show();
                });
                captureAndAnalyzeWithGemini();
            }
        });
    }

    /**
     * 현재 카메라 화면을 캡처해서 Gemini AI로 분석
     */
    private void captureAndAnalyzeWithGemini() {
        if (imageCapture == null) {
            mainHandler.post(() -> {
                showLoading(false);
                isProcessing = false;
                Toast.makeText(this, "카메라 초기화 중...", Toast.LENGTH_SHORT).show();
            });
            return;
        }

        imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                Bitmap bitmap = imageProxyToBitmap(image);
                image.close();

                if (bitmap != null) {
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
                                Toast.makeText(ScanActivity.this,
                                        "AI 분석 실패: " + error, Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        showLoading(false);
                        isProcessing = false;
                        Toast.makeText(ScanActivity.this,
                                "이미지 캡처 실패", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                mainHandler.post(() -> {
                    showLoading(false);
                    isProcessing = false;
                    Toast.makeText(ScanActivity.this,
                            "사진 촬영 실패", Toast.LENGTH_SHORT).show();
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

        // 식약처 API 먼저 시도, 실패하면 Gemini API로 fallback
        foodRepository.searchByFoodName(foodName, new FoodRepository.FoodCallback() {
            @Override
            public void onSuccess(NutritionData data) {
                runOnUiThread(() -> processNutritionData(data));
            }

            @Override
            public void onError(String message) {
                // 식약처 API 실패 시 Gemini API로 fallback
                Log.d(TAG, "식약처 API 실패, Gemini API로 시도: " + message);
                geminiService.analyzeFoodByName(foodName, new GeminiService.GeminiCallback() {
                    @Override
                    public void onSuccess(NutritionData data) {
                        mainHandler.post(() -> processNutritionData(data));
                    }

                    @Override
                    public void onError(String error) {
                        mainHandler.post(() -> {
                            showLoading(false);
                            isProcessing = false;
                            Toast.makeText(ScanActivity.this,
                                    "음식 정보를 찾을 수 없습니다: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }
        });
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
     * JPEG 또는 YUV_420_888 형식 모두 지원
     */
    @androidx.camera.core.ExperimentalGetImage
    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            android.media.Image mediaImage = image.getImage();
            if (mediaImage == null) {
                Log.e(TAG, "mediaImage is null");
                return null;
            }

            int format = mediaImage.getFormat();
            android.media.Image.Plane[] planes = mediaImage.getPlanes();

            Log.d(TAG, "Image format: " + format + ", planes: " + planes.length);

            Bitmap bitmap;

            if (format == android.graphics.ImageFormat.JPEG || planes.length == 1) {
                // JPEG 형식: 바로 디코딩
                ByteBuffer buffer = planes[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } else {
                // YUV_420_888 형식: NV21로 변환
                bitmap = convertYuvToBitmap(mediaImage);
            }

            if (bitmap == null) {
                Log.e(TAG, "Bitmap conversion returned null");
                return null;
            }

            // 회전 보정
            int rotation = image.getImageInfo().getRotationDegrees();
            if (rotation != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                bitmap.recycle();
                bitmap = rotatedBitmap;
            }

            Log.d(TAG, "Image converted successfully: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Image conversion failed: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * YUV_420_888 이미지를 Bitmap으로 변환
     */
    private Bitmap convertYuvToBitmap(android.media.Image mediaImage) {
        int width = mediaImage.getWidth();
        int height = mediaImage.getHeight();

        android.media.Image.Plane[] planes = mediaImage.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int yRowStride = planes[0].getRowStride();
        int uvRowStride = planes[1].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();

        // NV21 배열 생성
        byte[] nv21 = new byte[width * height * 3 / 2];

        // Y 평면 복사
        int pos = 0;
        if (yRowStride == width) {
            yBuffer.get(nv21, 0, width * height);
            pos = width * height;
        } else {
            for (int row = 0; row < height; row++) {
                yBuffer.position(row * yRowStride);
                yBuffer.get(nv21, pos, width);
                pos += width;
            }
        }

        // UV 평면 복사
        int uvHeight = height / 2;
        int uvWidth = width / 2;

        if (uvPixelStride == 2 && uvRowStride == width) {
            vBuffer.get(nv21, pos, uvWidth * uvHeight * 2);
        } else {
            for (int row = 0; row < uvHeight; row++) {
                for (int col = 0; col < uvWidth; col++) {
                    int uvIndex = row * uvRowStride + col * uvPixelStride;
                    nv21[pos++] = vBuffer.get(uvIndex);
                    nv21[pos++] = uBuffer.get(uvIndex);
                }
            }
        }

        android.graphics.YuvImage yuvImage = new android.graphics.YuvImage(
                nv21, android.graphics.ImageFormat.NV21, width, height, null);

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, width, height), 90, out);

        byte[] imageBytes = out.toByteArray();
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private void processNutritionData(NutritionData data) {
        showLoading(false);

        String userId = firebaseRepository.getCurrentUserId();
        if (userId == null) {
            Toast.makeText(this, "로그인이 필요합니다", Toast.LENGTH_SHORT).show();
            return;
        }

        // 영양 데이터 저장 (나중에 기록용)
        lastNutritionData = data;

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
        if (lastResult == null || lastNutritionData == null) return;

        showLoading(true);

        String userId = firebaseRepository.getCurrentUserId();
        if (userId == null) {
            showLoading(false);
            return;
        }

        // 1. 영양 기록 저장
        boolean isHealthy = !lastResult.isMonster();
        String resultType = lastResult.isMonster() ? "MONSTER" : "ITEM";
        NutritionRecord record = NutritionRecord.fromNutritionData(lastNutritionData, userId, isHealthy, resultType);

        firebaseRepository.saveNutritionRecord(record, new FirebaseRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                // 2. 퀘스트 진행: 음식 스캔
                progressQuests(userId, isHealthy);
            }

            @Override
            public void onError(String message) {
                // 기록 실패해도 계속 진행
                progressQuests(userId, isHealthy);
            }
        });

        // 3. 몬스터 또는 아이템 저장
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

    /**
     * 퀘스트 진행 처리
     */
    private void progressQuests(String userId, boolean isHealthy) {
        // 음식 스캔 퀘스트 진행
        firebaseRepository.progressQuestByType(userId, DailyQuest.QuestType.SCAN_FOOD.name(), 1,
                new FirebaseRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {}
                    @Override
                    public void onError(String message) {}
                });

        // 건강한 음식 스캔 퀘스트 진행 (아이템인 경우만)
        if (isHealthy) {
            firebaseRepository.progressQuestByType(userId, DailyQuest.QuestType.SCAN_HEALTHY.name(), 1,
                    new FirebaseRepository.SimpleCallback() {
                        @Override
                        public void onSuccess() {}
                        @Override
                        public void onError(String message) {}
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
