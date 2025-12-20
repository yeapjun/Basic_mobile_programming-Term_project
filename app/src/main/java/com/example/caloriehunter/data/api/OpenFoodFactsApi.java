package com.example.caloriehunter.data.api;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

/**
 * Open Food Facts API 인터페이스
 * https://world.openfoodfacts.org/api/v2/product/{barcode}
 */
public interface OpenFoodFactsApi {

    @GET("api/v2/product/{barcode}.json")
    Call<OpenFoodFactsResponse> getProductByBarcode(@Path("barcode") String barcode);

    /**
     * API 응답 모델
     */
    class OpenFoodFactsResponse {
        public int status;           // 1 = found, 0 = not found
        public String status_verbose;
        public Product product;
    }

    class Product {
        public String product_name;
        public String product_name_ko;  // 한국어 이름
        public String brands;
        public String image_url;
        public Nutriments nutriments;
    }

    class Nutriments {
        // 100g당 영양소 (기본값)
        public float energy_kcal_100g;          // 칼로리
        public float proteins_100g;              // 단백질
        public float fat_100g;                   // 지방
        public float saturated_fat_100g;         // 포화지방 (표기 주의)
        public float trans_fat_100g;             // 트랜스지방
        public float carbohydrates_100g;         // 탄수화물
        public float sugars_100g;                // 당류
        public float fiber_100g;                 // 식이섬유
        public float sodium_100g;                // 나트륨 (g 단위로 올 수 있음)
        public float salt_100g;                  // 소금

        // 1회 제공량당 (serving)
        public float energy_kcal_serving;
        public float proteins_serving;
        public float fat_serving;
        public float saturated_fat_serving;
        public float trans_fat_serving;
        public float carbohydrates_serving;
        public float sugars_serving;
        public float fiber_serving;
        public float sodium_serving;

        // 대체 필드명들 (API 일관성 없음)
        public Float energy_value;
        public Float proteins;
        public Float fat;
        public Float carbohydrates;
        public Float sugars;
        public Float fiber;
        public Float sodium;

        /**
         * 나트륨 mg 단위로 변환 (API가 g 또는 mg로 올 수 있음)
         * Open Food Facts는 일반적으로 나트륨을 g 단위로 반환
         */
        public float getSodiumMg() {
            float value = sodium_100g;
            if (value == 0 && sodium != null) {
                value = sodium;
            }
            // 일반적으로 나트륨 권장량은 2000mg = 2g
            // 100g당 2g(2000mg) 이하면 g 단위로 간주하고 mg로 변환
            // 100g당 2000mg 이상이면 이미 mg 단위로 간주
            if (value > 0 && value <= 5) {
                // g 단위로 간주 → mg로 변환
                return value * 1000;
            }
            return value;
        }
    }
}
