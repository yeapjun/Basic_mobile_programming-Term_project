package com.example.caloriehunter.api;

import android.graphics.Bitmap;
import android.util.Log;

import com.example.caloriehunter.BuildConfig;
import com.example.caloriehunter.data.model.NutritionData;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONObject;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Gemini 공식 SDK를 사용한 음식 분석 서비스
 */
public class GeminiService {
    private static final String TAG = "GeminiService";
    // Gemini 모델 이름
    private static final String MODEL_NAME = "gemini-2.5-flash-lite";

    private static GeminiService instance;
    private final GenerativeModelFutures model;

    // 콜백 인터페이스
    public interface GeminiCallback {
        void onSuccess(NutritionData nutritionData);
        void onError(String error);
    }

    private GeminiService() {
        // ★ 중요: 여기서 SDK 모델을 생성합니다. (OkHttp 필요 없음)
        GenerativeModel gm = new GenerativeModel(MODEL_NAME, BuildConfig.GEMINI_API_KEY);
        this.model = GenerativeModelFutures.from(gm);
    }

    public static synchronized GeminiService getInstance() {
        if (instance == null) {
            instance = new GeminiService();
        }
        return instance;
    }

    /**
     * 음식 이미지를 분석하여 영양 정보 추정
     */
    public void analyzeFoodImage(Bitmap foodImage, GeminiCallback callback) {
        try {
            // 이미지 크기 조정 (너무 크면 API 오류 발생)
            Bitmap resizedImage = resizeBitmap(foodImage, 1024);
            Log.d(TAG, "Image size: " + resizedImage.getWidth() + "x" + resizedImage.getHeight());

            String prompt = buildAnalysisPrompt();

            // SDK를 사용하면 이미지를 Base64로 바꿀 필요 없이 바로 넣으면 됩니다!
            Content content = new Content.Builder()
                    .addImage(resizedImage) // 비트맵 직접 입력
                    .addText(prompt)
                    .build();

            // 요청 전송
            ListenableFuture<GenerateContentResponse> response = model.generateContent(content);
            handleResponse(response, callback);
        } catch (Exception e) {
            Log.e(TAG, "Failed to prepare image for analysis", e);
            callback.onError("이미지 준비 실패: " + e.getMessage());
        }
    }

    /**
     * 이미지 크기 조정 (maxSize 이하로)
     */
    private Bitmap resizeBitmap(Bitmap original, int maxSize) {
        int width = original.getWidth();
        int height = original.getHeight();

        if (width <= maxSize && height <= maxSize) {
            return original;
        }

        float scale = Math.min((float) maxSize / width, (float) maxSize / height);
        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);

        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }

    /**
     * 음식 이름으로 영양 정보 추정 (텍스트 기반)
     */
    public void analyzeFoodByName(String foodName, GeminiCallback callback) {
        Log.d(TAG, "========== Gemini 텍스트 분석 시작 ==========");
        Log.d(TAG, "음식명: " + foodName);

        try {
            String prompt = buildTextAnalysisPrompt(foodName);

            // 텍스트 요청 생성
            Content content = new Content.Builder()
                    .addText(prompt)
                    .build();

            // 요청 전송
            ListenableFuture<GenerateContentResponse> response = model.generateContent(content);
            handleResponse(response, callback);
        } catch (Exception e) {
            Log.e(TAG, "Gemini 요청 생성 실패: " + e.getMessage(), e);
            callback.onError("요청 생성 실패: " + e.getMessage());
        }
    }

    // 결과 처리 공통 함수
    private void handleResponse(ListenableFuture<GenerateContentResponse> response, GeminiCallback callback) {
        Executor executor = Executors.newSingleThreadExecutor();

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                try {
                    String resultText = result.getText();
                    Log.d(TAG, "Gemini Response: " + resultText);

                    if (resultText == null || resultText.isEmpty()) {
                        callback.onError("응답 내용이 없습니다.");
                        return;
                    }

                    NutritionData data = parseGeminiResponse(resultText);
                    callback.onSuccess(data);

                } catch (Exception e) {
                    Log.e(TAG, "Parsing error", e);
                    callback.onError("분석 결과를 처리하는 중 오류가 발생했습니다: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "API Error: " + t.getClass().getSimpleName() + " - " + t.getMessage(), t);

                String errorMessage;
                String msg = t.getMessage() != null ? t.getMessage().toLowerCase() : "";

                if (msg.contains("api key") || msg.contains("unauthorized") || msg.contains("401")) {
                    errorMessage = "API 키가 유효하지 않습니다";
                } else if (msg.contains("quota") || msg.contains("rate limit") || msg.contains("429")) {
                    errorMessage = "API 요청 한도 초과";
                } else if (msg.contains("network") || msg.contains("timeout") || msg.contains("connect")) {
                    errorMessage = "네트워크 연결 오류";
                } else if (msg.contains("not found") || msg.contains("404")) {
                    errorMessage = "API 엔드포인트를 찾을 수 없음";
                } else {
                    errorMessage = t.getMessage() != null ? t.getMessage() : "알 수 없는 오류";
                }

                callback.onError(errorMessage);
            }
        }, executor);
    }

    // --- 아래는 프롬프트 및 파싱 로직 (기존 유지) ---

    private String buildAnalysisPrompt() {
        return "이 음식 이미지를 분석해주세요. 다음 JSON 형식으로만 응답해주세요:\n\n" +
                "{\n" +
                "  \"foodName\": \"음식 이름 (한글)\",\n" +
                "  \"calories\": 칼로리 (kcal, 숫자만),\n" +
                "  \"sugar\": 당류 (g, 숫자만),\n" +
                "  \"sodium\": 나트륨 (mg, 숫자만),\n" +
                "  \"saturatedFat\": 포화지방 (g, 숫자만),\n" +
                "  \"transFat\": 트랜스지방 (g, 숫자만),\n" +
                "  \"protein\": 단백질 (g, 숫자만),\n" +
                "  \"fiber\": 식이섬유 (g, 숫자만)\n" +
                "}\n\n" +
                "반드시 JSON 형식으로만 응답하세요. 마크다운 태그 없이 순수 JSON만 주세요.";
    }

    private String buildTextAnalysisPrompt(String foodName) {
        return "\"" + foodName + "\"의 일반적인 1인분 영양 정보를 추정해주세요. 다음 JSON 형식으로만 응답해주세요:\n\n" +
                "{\n" +
                "  \"foodName\": \"" + foodName + "\",\n" +
                "  \"calories\": 칼로리 (kcal, 숫자만),\n" +
                "  \"sugar\": 당류 (g, 숫자만),\n" +
                "  \"sodium\": 나트륨 (mg, 숫자만),\n" +
                "  \"saturatedFat\": 포화지방 (g, 숫자만),\n" +
                "  \"transFat\": 트랜스지방 (g, 숫자만),\n" +
                "  \"protein\": 단백질 (g, 숫자만),\n" +
                "  \"fiber\": 식이섬유 (g, 숫자만)\n" +
                "}\n\n" +
                "반드시 JSON 형식으로만 응답하세요. 마크다운 태그 없이 순수 JSON만 주세요.";
    }

    private NutritionData parseGeminiResponse(String responseText) throws Exception {
        String jsonStr = extractJson(responseText);
        JSONObject json = new JSONObject(jsonStr);

        NutritionData data = new NutritionData();
        data.setFoodName(json.optString("foodName", "알 수 없는 음식"));
        data.setCalories((float) json.optDouble("calories", 0));
        data.setSugar((float) json.optDouble("sugar", 0));
        data.setSodium((float) json.optDouble("sodium", 0));
        data.setSaturatedFat((float) json.optDouble("saturatedFat", 0));
        data.setTransFat((float) json.optDouble("transFat", 0));
        data.setProtein((float) json.optDouble("protein", 0));
        data.setFiber((float) json.optDouble("fiber", 0));
        data.setSource("Gemini AI");
        data.setConfidence(0.8f);

        return data;
    }

    private String extractJson(String text) {
        if (text.contains("```json")) {
            int start = text.indexOf("```json") + 7;
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).trim();
            }
        }
        if (text.contains("```")) {
            int start = text.indexOf("```") + 3;
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).trim();
            }
        }
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
