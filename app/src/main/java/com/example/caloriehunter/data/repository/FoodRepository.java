package com.example.caloriehunter.data.repository;

import android.util.Log;

import com.example.caloriehunter.BuildConfig;
import com.example.caloriehunter.data.api.OpenFoodFactsApi;
import com.example.caloriehunter.data.api.FoodSafetyApi;
import com.example.caloriehunter.data.model.NutritionData;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

/**
 * 음식 데이터 레포지토리
 * Open Food Facts + 식약처 API 하이브리드 조회
 */
public class FoodRepository {

    private static final String TAG = "FoodRepository";
    private static final String OPEN_FOOD_FACTS_BASE_URL = "https://world.openfoodfacts.org/";
    private static final String FOOD_SAFETY_BASE_URL = "https://apis.data.go.kr/";

    private static FoodRepository instance;
    private final OpenFoodFactsApi openFoodFactsApi;
    private final FoodSafetyApi foodSafetyApi;

    // 식약처 API 키 (공공데이터포털에서 발급)
    private String foodSafetyApiKey = BuildConfig.MFDS_API_KEY;

    public interface FoodCallback {
        void onSuccess(NutritionData data);
        void onError(String message);
    }

    private FoodRepository() {
        // OkHttp 클라이언트 설정
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        // Open Food Facts API
        Retrofit openFoodFactsRetrofit = new Retrofit.Builder()
                .baseUrl(OPEN_FOOD_FACTS_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        openFoodFactsApi = openFoodFactsRetrofit.create(OpenFoodFactsApi.class);

        // 식약처 API
        Retrofit foodSafetyRetrofit = new Retrofit.Builder()
                .baseUrl(FOOD_SAFETY_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        foodSafetyApi = foodSafetyRetrofit.create(FoodSafetyApi.class);
    }

    public static synchronized FoodRepository getInstance() {
        if (instance == null) {
            instance = new FoodRepository();
        }
        return instance;
    }

    public void setFoodSafetyApiKey(String apiKey) {
        this.foodSafetyApiKey = apiKey;
    }

    /**
     * 바코드로 음식 조회 (Priority 1: Open Food Facts)
     */
    public void searchByBarcode(String barcode, FoodCallback callback) {
        Log.d(TAG, "Searching barcode: " + barcode);

        openFoodFactsApi.getProductByBarcode(barcode).enqueue(new Callback<OpenFoodFactsApi.OpenFoodFactsResponse>() {
            @Override
            public void onResponse(Call<OpenFoodFactsApi.OpenFoodFactsResponse> call,
                                   Response<OpenFoodFactsApi.OpenFoodFactsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    OpenFoodFactsApi.OpenFoodFactsResponse data = response.body();

                    if (data.status == 1 && data.product != null) {
                        NutritionData nutrition = convertFromOpenFoodFacts(data.product, barcode);
                        callback.onSuccess(nutrition);
                    } else {
                        callback.onError("제품을 찾을 수 없습니다");
                    }
                } else {
                    callback.onError("API 응답 오류: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<OpenFoodFactsApi.OpenFoodFactsResponse> call, Throwable t) {
                Log.e(TAG, "API call failed", t);
                callback.onError("네트워크 오류: " + t.getMessage());
            }
        });
    }

    /**
     * 음식명으로 검색 (Priority 2: 식약처 API)
     */
    public void searchByFoodName(String foodName, FoodCallback callback) {
        if (foodSafetyApiKey.isEmpty()) {
            callback.onError("식약처 API 키가 설정되지 않았습니다");
            return;
        }

        Log.d(TAG, "========== 식약처 API 검색 시작 ==========");
        Log.d(TAG, "검색어: " + foodName);
        Log.d(TAG, "API 키 설정됨: " + (foodSafetyApiKey.length() > 10 ? "예 (길이: " + foodSafetyApiKey.length() + ")" : "아니오"));

        foodSafetyApi.searchByFoodName(foodSafetyApiKey, foodName, 1, 1, "json")
                .enqueue(new Callback<FoodSafetyApi.FoodSafetyResponse>() {
                    @Override
                    public void onResponse(Call<FoodSafetyApi.FoodSafetyResponse> call,
                                           Response<FoodSafetyApi.FoodSafetyResponse> response) {
                        Log.d(TAG, "식약처 API 응답 코드: " + response.code());

                        if (response.isSuccessful() && response.body() != null) {
                            FoodSafetyApi.FoodSafetyResponse data = response.body();

                            if (data.body != null && data.body.items != null && !data.body.items.isEmpty()) {
                                FoodSafetyApi.FoodItem item = data.body.items.get(0);
                                Log.d(TAG, "✅ 식약처 API 성공! 음식명: " + item.FOOD_NM_KR);
                                Log.d(TAG, "칼로리: " + item.getCalories() + ", 단백질: " + item.getProtein());
                                NutritionData nutrition = convertFromFoodSafety(item);
                                callback.onSuccess(nutrition);
                            } else {
                                Log.w(TAG, "❌ 식약처 API: 검색 결과 없음");
                                callback.onError("음식을 찾을 수 없습니다");
                            }
                        } else {
                            Log.e(TAG, "❌ 식약처 API 오류: " + response.code() + " - " + response.message());
                            try {
                                if (response.errorBody() != null) {
                                    Log.e(TAG, "에러 바디: " + response.errorBody().string());
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "에러 바디 읽기 실패", e);
                            }
                            callback.onError("API 응답 오류: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<FoodSafetyApi.FoodSafetyResponse> call, Throwable t) {
                        Log.e(TAG, "❌ 식약처 API 네트워크 오류: " + t.getMessage(), t);
                        callback.onError("네트워크 오류: " + t.getMessage());
                    }
                });
    }

    /**
     * Open Food Facts 응답 → NutritionData 변환
     */
    private NutritionData convertFromOpenFoodFacts(OpenFoodFactsApi.Product product, String barcode) {
        OpenFoodFactsApi.Nutriments n = product.nutriments;

        // 음식명 결정 (한국어 우선)
        String foodName = product.product_name_ko;
        if (foodName == null || foodName.isEmpty()) {
            foodName = product.product_name;
        }
        if (foodName == null || foodName.isEmpty()) {
            foodName = "알 수 없는 제품";
        }

        return new NutritionData.Builder()
                .foodName(foodName)
                .barcode(barcode)
                .imageUrl(product.image_url)
                .calories(n != null ? n.energy_kcal_100g : 0)
                .protein(n != null ? n.proteins_100g : 0)
                .fat(n != null ? n.fat_100g : 0)
                .saturatedFat(n != null ? n.saturated_fat_100g : 0)
                .transFat(n != null ? n.trans_fat_100g : 0)
                .carbohydrates(n != null ? n.carbohydrates_100g : 0)
                .sugar(n != null ? n.sugars_100g : 0)
                .fiber(n != null ? n.fiber_100g : 0)
                .sodium(n != null ? n.getSodiumMg() : 0)
                .source("openfoodfacts")
                .confidence(0.9f)
                .build();
    }

    /**
     * 식약처 API 응답 → NutritionData 변환
     */
    private NutritionData convertFromFoodSafety(FoodSafetyApi.FoodItem item) {
        return new NutritionData.Builder()
                .foodName(item.FOOD_NM_KR)
                .calories(item.getCalories())
                .protein(item.getProtein())
                .fat(item.getFat())
                .saturatedFat(item.getSaturatedFat())
                .transFat(item.getTransFat())
                .carbohydrates(item.getCarbohydrates())
                .sugar(item.getSugar())
                .fiber(0)  // 식약처 API에 식이섬유 없음
                .sodium(item.getSodium())
                .source("foodsafety")
                .confidence(0.95f)
                .build();
    }
}
