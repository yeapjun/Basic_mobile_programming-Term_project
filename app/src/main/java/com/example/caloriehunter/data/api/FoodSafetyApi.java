package com.example.caloriehunter.data.api;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import java.util.List;

/**
 * 식품의약품안전처 식품영양성분 API 인터페이스
 * 공공데이터포털 (data.go.kr) 기반
 */
public interface FoodSafetyApi {

    /**
     * 식품명으로 검색
     * @param serviceKey 공공데이터포털 API 키 (URL 인코딩된 상태로 전달)
     * @param foodName 검색할 음식명
     * @param pageNo 페이지 번호
     * @param numOfRows 한 페이지 결과 수
     */
    @GET("1471000/FoodNtrCpntDbInfo02/getFoodNtrCpntDbInq02")
    Call<FoodSafetyResponse> searchByFoodName(
            @Query("serviceKey") String serviceKey,
            @Query("FOOD_NM_KR") String foodName,
            @Query("pageNo") int pageNo,
            @Query("numOfRows") int numOfRows,
            @Query("type") String type  // "json"
    );

    /**
     * API 응답 모델
     */
    class FoodSafetyResponse {
        public Header header;
        public Body body;
    }

    class Header {
        public String resultCode;
        public String resultMsg;
    }

    class Body {
        public int totalCount;
        public int pageNo;
        public int numOfRows;
        public List<FoodItem> items;
    }

    class FoodItem {
        // 기본 정보
        public String FOOD_NM_KR;          // 식품명 (한글)
        public String SERVING_SIZE;      // 1회 제공량
        public String NUTR_CONT1;        // 칼로리 (kcal)
        public String NUTR_CONT2;        // 탄수화물 (g)
        public String NUTR_CONT3;        // 단백질 (g)
        public String NUTR_CONT4;        // 지방 (g)
        public String NUTR_CONT5;        // 당류 (g)
        public String NUTR_CONT6;        // 나트륨 (mg)
        public String NUTR_CONT7;        // 콜레스테롤 (mg)
        public String NUTR_CONT8;        // 포화지방 (g)
        public String NUTR_CONT9;        // 트랜스지방 (g)

        // 안전하게 float 변환
        public float getCalories() { return parseFloat(NUTR_CONT1); }
        public float getCarbohydrates() { return parseFloat(NUTR_CONT2); }
        public float getProtein() { return parseFloat(NUTR_CONT3); }
        public float getFat() { return parseFloat(NUTR_CONT4); }
        public float getSugar() { return parseFloat(NUTR_CONT5); }
        public float getSodium() { return parseFloat(NUTR_CONT6); }
        public float getCholesterol() { return parseFloat(NUTR_CONT7); }
        public float getSaturatedFat() { return parseFloat(NUTR_CONT8); }
        public float getTransFat() { return parseFloat(NUTR_CONT9); }

        private float parseFloat(String value) {
            if (value == null || value.isEmpty() || value.equals("N/A")) {
                return 0f;
            }
            try {
                // 숫자가 아닌 문자 제거 (단위 등)
                String cleaned = value.replaceAll("[^0-9.]", "");
                return Float.parseFloat(cleaned);
            } catch (NumberFormatException e) {
                return 0f;
            }
        }
    }
}
