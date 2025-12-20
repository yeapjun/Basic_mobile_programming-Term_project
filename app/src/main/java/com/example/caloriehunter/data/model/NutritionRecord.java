package com.example.caloriehunter.data.model;

import com.google.firebase.database.Exclude;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 영양 기록 모델
 * 스캔한 음식의 영양 정보를 기록
 */
public class NutritionRecord {

    private String id;
    private String ownerId;
    private String date;          // "2025-12-18" 형식
    private String foodName;
    private float calories;
    private float protein;
    private float carbohydrate;
    private float fat;
    private float sugar;
    private float sodium;
    private float fiber;
    private float saturatedFat;
    private float transFat;
    private boolean isHealthy;    // 건강식 여부 (아이템이 됐는지)
    private String resultType;    // "MONSTER" or "ITEM"
    private long timestamp;

    public NutritionRecord() {
        // Firebase 기본 생성자
    }

    /**
     * NutritionData로부터 기록 생성
     */
    @Exclude
    public static NutritionRecord fromNutritionData(NutritionData data, String ownerId, boolean isHealthy, String resultType) {
        NutritionRecord record = new NutritionRecord();
        record.id = UUID.randomUUID().toString();
        record.ownerId = ownerId;
        record.date = getTodayDateString();
        record.foodName = data.getFoodName();
        record.calories = data.getCalories();
        record.protein = data.getProtein();
        record.carbohydrate = data.getCarbohydrates();
        record.fat = data.getFat();
        record.sugar = data.getSugar();
        record.sodium = data.getSodium();
        record.fiber = data.getFiber();
        record.saturatedFat = data.getSaturatedFat();
        record.transFat = data.getTransFat();
        record.isHealthy = isHealthy;
        record.resultType = resultType;
        record.timestamp = System.currentTimeMillis();
        return record;
    }

    /**
     * 오늘 날짜 문자열
     */
    @Exclude
    public static String getTodayDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date());
    }

    /**
     * N일 전 날짜 문자열
     */
    @Exclude
    public static String getDateStringDaysAgo(int daysAgo) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        long time = System.currentTimeMillis() - (daysAgo * 24L * 60 * 60 * 1000);
        return sdf.format(new Date(time));
    }

    /**
     * Firebase 저장용 Map
     */
    @Exclude
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("ownerId", ownerId);
        map.put("date", date);
        map.put("foodName", foodName);
        map.put("calories", calories);
        map.put("protein", protein);
        map.put("carbohydrate", carbohydrate);
        map.put("fat", fat);
        map.put("sugar", sugar);
        map.put("sodium", sodium);
        map.put("fiber", fiber);
        map.put("saturatedFat", saturatedFat);
        map.put("transFat", transFat);
        map.put("isHealthy", isHealthy);
        map.put("resultType", resultType);
        map.put("timestamp", timestamp);
        return map;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getFoodName() { return foodName; }
    public void setFoodName(String foodName) { this.foodName = foodName; }

    public float getCalories() { return calories; }
    public void setCalories(float calories) { this.calories = calories; }

    public float getProtein() { return protein; }
    public void setProtein(float protein) { this.protein = protein; }

    public float getCarbohydrate() { return carbohydrate; }
    public void setCarbohydrate(float carbohydrate) { this.carbohydrate = carbohydrate; }

    public float getFat() { return fat; }
    public void setFat(float fat) { this.fat = fat; }

    public float getSugar() { return sugar; }
    public void setSugar(float sugar) { this.sugar = sugar; }

    public float getSodium() { return sodium; }
    public void setSodium(float sodium) { this.sodium = sodium; }

    public float getFiber() { return fiber; }
    public void setFiber(float fiber) { this.fiber = fiber; }

    public float getSaturatedFat() { return saturatedFat; }
    public void setSaturatedFat(float saturatedFat) { this.saturatedFat = saturatedFat; }

    public float getTransFat() { return transFat; }
    public void setTransFat(float transFat) { this.transFat = transFat; }

    public boolean getIsHealthy() { return isHealthy; }
    public void setIsHealthy(boolean isHealthy) { this.isHealthy = isHealthy; }

    public String getResultType() { return resultType; }
    public void setResultType(String resultType) { this.resultType = resultType; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
