package com.example.caloriehunter.data.model;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 영양 통계 계산 헬퍼 클래스
 */
public class NutritionStats {

    private float totalCalories;
    private float totalProtein;
    private float totalCarbohydrate;
    private float totalFat;
    private float totalSugar;
    private float totalSodium;
    private float totalFiber;
    private int healthyCount;
    private int unhealthyCount;
    private int totalCount;

    // 일일 권장량 (식약처 기준)
    public static final float DAILY_CALORIES = 2000f;
    public static final float DAILY_PROTEIN = 55f;
    public static final float DAILY_CARBOHYDRATE = 324f;
    public static final float DAILY_FAT = 54f;
    public static final float DAILY_SUGAR = 50f;
    public static final float DAILY_SODIUM = 2000f;
    public static final float DAILY_FIBER = 25f;

    public NutritionStats() {
        reset();
    }

    /**
     * 기록 리스트로부터 통계 계산
     */
    public static NutritionStats fromRecords(List<NutritionRecord> records) {
        NutritionStats stats = new NutritionStats();

        if (records == null || records.isEmpty()) {
            return stats;
        }

        for (NutritionRecord record : records) {
            stats.totalCalories += record.getCalories();
            stats.totalProtein += record.getProtein();
            stats.totalCarbohydrate += record.getCarbohydrate();
            stats.totalFat += record.getFat();
            stats.totalSugar += record.getSugar();
            stats.totalSodium += record.getSodium();
            stats.totalFiber += record.getFiber();

            if (record.getIsHealthy()) {
                stats.healthyCount++;
            } else {
                stats.unhealthyCount++;
            }
            stats.totalCount++;
        }

        return stats;
    }

    /**
     * 일별 통계 Map 생성 (최근 7일)
     */
    public static Map<String, NutritionStats> getDailyStats(List<NutritionRecord> records) {
        Map<String, NutritionStats> dailyStats = new HashMap<>();

        // 최근 7일 초기화
        for (int i = 0; i < 7; i++) {
            String date = NutritionRecord.getDateStringDaysAgo(i);
            dailyStats.put(date, new NutritionStats());
        }

        if (records == null) return dailyStats;

        // 기록별로 날짜 그룹핑
        for (NutritionRecord record : records) {
            String date = record.getDate();
            NutritionStats stats = dailyStats.get(date);
            if (stats != null) {
                stats.addRecord(record);
            }
        }

        return dailyStats;
    }

    /**
     * 기록 추가
     */
    public void addRecord(NutritionRecord record) {
        totalCalories += record.getCalories();
        totalProtein += record.getProtein();
        totalCarbohydrate += record.getCarbohydrate();
        totalFat += record.getFat();
        totalSugar += record.getSugar();
        totalSodium += record.getSodium();
        totalFiber += record.getFiber();

        if (record.getIsHealthy()) {
            healthyCount++;
        } else {
            unhealthyCount++;
        }
        totalCount++;
    }

    /**
     * 초기화
     */
    public void reset() {
        totalCalories = 0;
        totalProtein = 0;
        totalCarbohydrate = 0;
        totalFat = 0;
        totalSugar = 0;
        totalSodium = 0;
        totalFiber = 0;
        healthyCount = 0;
        unhealthyCount = 0;
        totalCount = 0;
    }

    /**
     * 건강 점수 계산 (0~100)
     * 건강식 비율 + 영양 균형 점수
     */
    public int getHealthScore() {
        if (totalCount == 0) return 0;

        // 건강식 비율 (50점 만점)
        float healthyRatio = (float) healthyCount / totalCount;
        int healthyScore = (int) (healthyRatio * 50);

        // 영양 균형 점수 (50점 만점)
        int balanceScore = 0;

        // 단백질 적정 (10점)
        float proteinRatio = totalProtein / DAILY_PROTEIN;
        if (proteinRatio >= 0.8f && proteinRatio <= 1.5f) balanceScore += 10;
        else if (proteinRatio >= 0.5f) balanceScore += 5;

        // 당류 적정 (15점) - 낮을수록 좋음
        float sugarRatio = totalSugar / DAILY_SUGAR;
        if (sugarRatio <= 0.5f) balanceScore += 15;
        else if (sugarRatio <= 0.8f) balanceScore += 10;
        else if (sugarRatio <= 1.0f) balanceScore += 5;

        // 나트륨 적정 (15점) - 낮을수록 좋음
        float sodiumRatio = totalSodium / DAILY_SODIUM;
        if (sodiumRatio <= 0.5f) balanceScore += 15;
        else if (sodiumRatio <= 0.8f) balanceScore += 10;
        else if (sodiumRatio <= 1.0f) balanceScore += 5;

        // 식이섬유 적정 (10점) - 높을수록 좋음
        float fiberRatio = totalFiber / DAILY_FIBER;
        if (fiberRatio >= 1.0f) balanceScore += 10;
        else if (fiberRatio >= 0.7f) balanceScore += 7;
        else if (fiberRatio >= 0.4f) balanceScore += 4;

        return Math.min(100, healthyScore + balanceScore);
    }

    /**
     * 건강 등급 반환
     */
    public String getHealthGrade() {
        int score = getHealthScore();
        if (score >= 80) return "A";
        if (score >= 60) return "B";
        if (score >= 40) return "C";
        if (score >= 20) return "D";
        return "F";
    }

    /**
     * 등급별 색상
     */
    public String getHealthGradeColor() {
        String grade = getHealthGrade();
        switch (grade) {
            case "A": return "#4CAF50";  // 초록
            case "B": return "#8BC34A";  // 연두
            case "C": return "#FFC107";  // 노랑
            case "D": return "#FF9800";  // 주황
            default: return "#F44336";   // 빨강
        }
    }

    /**
     * 권장량 대비 퍼센트
     */
    public int getCaloriesPercent() {
        return (int) (totalCalories / DAILY_CALORIES * 100);
    }

    public int getProteinPercent() {
        return (int) (totalProtein / DAILY_PROTEIN * 100);
    }

    public int getCarbohydratePercent() {
        return (int) (totalCarbohydrate / DAILY_CARBOHYDRATE * 100);
    }

    public int getFatPercent() {
        return (int) (totalFat / DAILY_FAT * 100);
    }

    public int getSugarPercent() {
        return (int) (totalSugar / DAILY_SUGAR * 100);
    }

    public int getSodiumPercent() {
        return (int) (totalSodium / DAILY_SODIUM * 100);
    }

    public int getFiberPercent() {
        return (int) (totalFiber / DAILY_FIBER * 100);
    }

    // Getters
    public float getTotalCalories() { return totalCalories; }
    public float getTotalProtein() { return totalProtein; }
    public float getTotalCarbohydrate() { return totalCarbohydrate; }
    public float getTotalFat() { return totalFat; }
    public float getTotalSugar() { return totalSugar; }
    public float getTotalSodium() { return totalSodium; }
    public float getTotalFiber() { return totalFiber; }
    public int getHealthyCount() { return healthyCount; }
    public int getUnhealthyCount() { return unhealthyCount; }
    public int getTotalCount() { return totalCount; }
}
