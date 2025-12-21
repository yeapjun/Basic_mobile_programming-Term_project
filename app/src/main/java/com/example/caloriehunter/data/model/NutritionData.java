package com.example.caloriehunter.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * 영양 성분 데이터 모델
 * Open Food Facts API 응답을 통합하는 구조
 */
public class NutritionData {

    private String foodName;
    private String barcode;
    private String imageUrl;

    // 기본 영양소 (g 단위, 나트륨만 mg)
    private float calories;      // kcal
    private float protein;       // 단백질 (g)
    private float fat;           // 지방 (g)
    private float saturatedFat;  // 포화지방 (g)
    private float transFat;      // 트랜스지방 (g)
    private float carbohydrates; // 탄수화물 (g)
    private float sugar;         // 당류 (g)
    private float fiber;         // 식이섬유 (g)
    private float sodium;        // 나트륨 (mg)

    // 데이터 소스
    private String source;       // "openfoodfacts", "foodsafety", "gemini"
    private float confidence;    // 신뢰도 (0.0 ~ 1.0)
    private String servingSize;  // 1회 제공량 (예: "100g", "1개")

    // 기본 생성자
    public NutritionData() {}

    // Builder 패턴
    public static class Builder {
        private NutritionData data = new NutritionData();

        public Builder foodName(String name) { data.foodName = name; return this; }
        public Builder barcode(String barcode) { data.barcode = barcode; return this; }
        public Builder imageUrl(String url) { data.imageUrl = url; return this; }
        public Builder calories(float val) { data.calories = val; return this; }
        public Builder protein(float val) { data.protein = val; return this; }
        public Builder fat(float val) { data.fat = val; return this; }
        public Builder saturatedFat(float val) { data.saturatedFat = val; return this; }
        public Builder transFat(float val) { data.transFat = val; return this; }
        public Builder carbohydrates(float val) { data.carbohydrates = val; return this; }
        public Builder sugar(float val) { data.sugar = val; return this; }
        public Builder fiber(float val) { data.fiber = val; return this; }
        public Builder sodium(float val) { data.sodium = val; return this; }
        public Builder source(String src) { data.source = src; return this; }
        public Builder confidence(float conf) { data.confidence = conf; return this; }
        public Builder servingSize(String size) { data.servingSize = size; return this; }

        public NutritionData build() { return data; }
    }

    // Getters
    public String getFoodName() { return foodName; }
    public String getBarcode() { return barcode; }
    public String getImageUrl() { return imageUrl; }
    public float getCalories() { return calories; }
    public float getProtein() { return protein; }
    public float getFat() { return fat; }
    public float getSaturatedFat() { return saturatedFat; }
    public float getTransFat() { return transFat; }
    public float getCarbohydrates() { return carbohydrates; }
    public float getSugar() { return sugar; }
    public float getFiber() { return fiber; }
    public float getSodium() { return sodium; }
    public String getSource() { return source; }
    public float getConfidence() { return confidence; }
    public String getServingSize() { return servingSize; }

    // Setters
    public void setFoodName(String foodName) { this.foodName = foodName; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setCalories(float calories) { this.calories = calories; }
    public void setProtein(float protein) { this.protein = protein; }
    public void setFat(float fat) { this.fat = fat; }
    public void setSaturatedFat(float saturatedFat) { this.saturatedFat = saturatedFat; }
    public void setTransFat(float transFat) { this.transFat = transFat; }
    public void setCarbohydrates(float carbohydrates) { this.carbohydrates = carbohydrates; }
    public void setSugar(float sugar) { this.sugar = sugar; }
    public void setFiber(float fiber) { this.fiber = fiber; }
    public void setSodium(float sodium) { this.sodium = sodium; }
    public void setSource(String source) { this.source = source; }
    public void setConfidence(float confidence) { this.confidence = confidence; }
    public void setServingSize(String servingSize) { this.servingSize = servingSize; }
}
