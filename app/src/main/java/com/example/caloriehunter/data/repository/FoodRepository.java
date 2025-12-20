package com.example.caloriehunter.data.repository;

import com.example.caloriehunter.data.api.OpenFoodFactsApi;
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
 * Open Food Facts API로 바코드 조회
 */
public class FoodRepository {

    private static final String OPEN_FOOD_FACTS_BASE_URL = "https://world.openfoodfacts.org/";

    private static FoodRepository instance;
    private final OpenFoodFactsApi openFoodFactsApi;

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
    }

    public static synchronized FoodRepository getInstance() {
        if (instance == null) {
            instance = new FoodRepository();
        }
        return instance;
    }

    /**
     * 바코드로 음식 조회 (Open Food Facts API)
     */
    public void searchByBarcode(String barcode, FoodCallback callback) {
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
}
