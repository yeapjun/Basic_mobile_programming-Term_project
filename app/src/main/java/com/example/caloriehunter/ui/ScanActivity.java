package com.example.caloriehunter.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
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
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.example.caloriehunter.R;
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

                // 후면 카메라
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // 바인딩
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

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
        input.setHint("음식명을 입력하세요");

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

        // 식약처 API 사용 (수동 검색용)
        // MVP에서는 간단히 기본 데이터 생성
        NutritionData mockData = new NutritionData.Builder()
                .foodName(foodName)
                .calories(200)
                .protein(5)
                .sugar(10)
                .sodium(300)
                .saturatedFat(3)
                .transFat(0)
                .fiber(2)
                .source("manual")
                .confidence(0.5f)
                .build();

        processNutritionData(mockData);
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
