package com.example.caloriehunter.game;

import com.example.caloriehunter.data.model.Item;
import com.example.caloriehunter.data.model.Monster;
import com.example.caloriehunter.data.model.NutritionData;

import java.util.UUID;

/**
 * 음식 분석 엔진
 * 영양 성분을 기반으로 몬스터 또는 아이템을 생성하는 핵심 게임 로직
 */
public class FoodAnalyzer {

    // ========== 일일 권장량 기준 (식약처 기준) ==========
    private static final float DAILY_SUGAR = 50f;           // 당류 50g
    private static final float DAILY_SODIUM = 2000f;        // 나트륨 2000mg
    private static final float DAILY_SAT_FAT = 15f;         // 포화지방 15g
    private static final float DAILY_TRANS_FAT = 2f;        // 트랜스지방 2g (WHO)
    private static final float DAILY_PROTEIN = 55f;         // 단백질 55g
    private static final float DAILY_FIBER = 25f;           // 식이섬유 25g

    // ========== 판정 임계값 ==========
    private static final float SUGAR_THRESHOLD = 15f;       // 당류 15g 이상 → 몬스터 경향
    private static final float SAT_FAT_THRESHOLD = 5f;      // 포화지방 5g 이상 → 몬스터 경향
    private static final float TRANS_FAT_THRESHOLD = 0.5f;  // 트랜스지방 0.5g 이상 → 강력 몬스터
    private static final float SODIUM_THRESHOLD = 600f;     // 나트륨 600mg 이상 → 몬스터 경향
    private static final float PROTEIN_THRESHOLD = 10f;     // 단백질 10g 이상 → 무기
    private static final float FIBER_THRESHOLD = 3f;        // 식이섬유 3g 이상 → 포션

    // ========== 스탯 변환 계수 ==========
    private static final float SUGAR_TO_HP = 1.5f;
    private static final float SODIUM_TO_DEFENSE = 0.015f;
    private static final float SAT_FAT_TO_ATTACK = 2.0f;
    private static final float TRANS_FAT_TO_POISON = 10.0f;
    private static final float PROTEIN_TO_ATTACK = 1.5f;
    private static final float FIBER_TO_HEAL = 3.0f;

    /**
     * 분석 결과를 담는 클래스
     */
    public static class AnalysisResult {
        public enum ResultType { MONSTER, ITEM }

        private ResultType type;
        private Monster monster;
        private Item item;
        private float dangerScore;
        private float healthScore;

        public ResultType getType() { return type; }
        public Monster getMonster() { return monster; }
        public Item getItem() { return item; }
        public float getDangerScore() { return dangerScore; }
        public float getHealthScore() { return healthScore; }
        public boolean isMonster() { return type == ResultType.MONSTER; }
    }

    /**
     * 메인 분석 함수
     * 영양 데이터를 받아 몬스터 또는 아이템 생성
     */
    public AnalysisResult analyze(NutritionData food, String ownerId) {
        AnalysisResult result = new AnalysisResult();

        // 1. 위험도/건강 점수 계산
        result.dangerScore = calculateDangerScore(food);
        result.healthScore = calculateHealthScore(food);

        // 2. 판정: 위험도가 높으면 몬스터, 아니면 아이템
        if (result.dangerScore > result.healthScore && result.dangerScore > 10) {
            result.type = AnalysisResult.ResultType.MONSTER;
            result.monster = generateMonster(food, result.dangerScore, ownerId);
        } else {
            result.type = AnalysisResult.ResultType.ITEM;
            result.item = generateItem(food, result.healthScore, ownerId);
        }

        return result;
    }

    /**
     * 위험도 점수 계산 (높을수록 불건강)
     */
    private float calculateDangerScore(NutritionData food) {
        float score = 0;

        // 당류 점수 (15g 초과분)
        if (food.getSugar() > SUGAR_THRESHOLD) {
            score += (food.getSugar() - SUGAR_THRESHOLD) * 2.0f;
        }

        // 포화지방 점수 (5g 초과분)
        if (food.getSaturatedFat() > SAT_FAT_THRESHOLD) {
            score += (food.getSaturatedFat() - SAT_FAT_THRESHOLD) * 3.0f;
        }

        // 트랜스지방 점수 (가장 해로움, 높은 가중치)
        if (food.getTransFat() > TRANS_FAT_THRESHOLD) {
            score += (food.getTransFat() - TRANS_FAT_THRESHOLD) * 15.0f;
        }

        // 나트륨 점수 (600mg 초과분)
        if (food.getSodium() > SODIUM_THRESHOLD) {
            score += (food.getSodium() - SODIUM_THRESHOLD) * 0.02f;
        }

        // 고칼로리 보너스 (500kcal 이상)
        if (food.getCalories() > 500) {
            score += (food.getCalories() - 500) * 0.03f;
        }

        // 혈당 영향 추정 (당류/식이섬유 비율)
        float giPenalty = estimateGlycemicImpact(food);
        score += giPenalty;

        return score;
    }

    /**
     * 건강 점수 계산 (높을수록 건강)
     */
    private float calculateHealthScore(NutritionData food) {
        float score = 0;

        // 단백질 점수
        if (food.getProtein() > PROTEIN_THRESHOLD) {
            score += (food.getProtein() - PROTEIN_THRESHOLD) * 2.5f;
        }

        // 식이섬유 점수
        if (food.getFiber() > FIBER_THRESHOLD) {
            score += (food.getFiber() - FIBER_THRESHOLD) * 3.0f;
        }

        // 저당 보너스 (5g 이하)
        if (food.getSugar() <= 5) {
            score += 15;
        }

        // 저나트륨 보너스 (300mg 이하)
        if (food.getSodium() <= 300) {
            score += 10;
        }

        // 영양밀도 보너스
        float nutritionDensity = calculateNutritionDensity(food);
        score += nutritionDensity * 2.0f;

        return score;
    }

    /**
     * 영양밀도 계산
     * (단백질 + 식이섬유) / 칼로리 × 100
     */
    private float calculateNutritionDensity(NutritionData food) {
        if (food.getCalories() <= 0) return 0;
        float beneficial = food.getProtein() + food.getFiber();
        return (beneficial / food.getCalories()) * 100;
    }

    /**
     * 혈당 영향 추정 (GI 대용)
     * 당류/식이섬유 비율로 추정
     */
    private float estimateGlycemicImpact(NutritionData food) {
        if (food.getFiber() <= 0) {
            // 식이섬유 없으면 당류가 그대로 혈당에 영향
            return food.getSugar() * 0.5f;
        }
        float ratio = food.getSugar() / food.getFiber();
        return Math.max(0, (ratio - 2) * 3);
    }

    /**
     * 몬스터 생성
     */
    private Monster generateMonster(NutritionData food, float dangerScore, String ownerId) {
        Monster monster = new Monster();

        monster.setId(UUID.randomUUID().toString());
        monster.setOwnerId(ownerId);
        monster.setFoodName(food.getFoodName());
        monster.setBarcode(food.getBarcode());

        // 속성 결정
        monster.setElement(determineElement(food));

        // 티어 결정
        monster.setTier(determineTier(dangerScore, food.getCalories()));

        // 이름 생성
        monster.setName(generateMonsterName(food.getFoodName(), monster.getElement(), monster.getTier()));

        // 스탯 계산
        int baseHp = Math.round(food.getSugar() * SUGAR_TO_HP);
        int calBonus = Math.round(food.getCalories() * 0.05f);
        monster.setMaxHp(Math.max(20, Math.min(baseHp + calBonus, 999)));
        monster.setHp(monster.getMaxHp());

        monster.setDefense(Math.max(0, Math.min(Math.round(food.getSodium() * SODIUM_TO_DEFENSE), 80)));
        monster.setAttack(Math.max(5, Math.min(Math.round(food.getSaturatedFat() * SAT_FAT_TO_ATTACK), 50)));
        monster.setPoisonDamage(Math.round(food.getTransFat() * TRANS_FAT_TO_POISON));

        // 상태 및 시간
        monster.setStatus("active");
        monster.setCreatedAt(System.currentTimeMillis());
        monster.setExpiresAt(monster.getCreatedAt() + (24 * 60 * 60 * 1000)); // 24시간

        // 원본 영양 데이터 저장
        monster.setOriginalSugar(food.getSugar());
        monster.setOriginalSodium(food.getSodium());
        monster.setOriginalSatFat(food.getSaturatedFat());
        monster.setOriginalTransFat(food.getTransFat());
        monster.setOriginalCalories(food.getCalories());

        return monster;
    }

    /**
     * 몬스터 속성 결정
     */
    private String determineElement(NutritionData food) {
        // 트랜스지방이 높으면 최우선 toxic
        if (food.getTransFat() > 1.0f) {
            return "toxic";
        }

        // 가장 높은 수치의 영양소가 속성 결정
        float sugarScore = food.getSugar();
        float sodiumScore = food.getSodium() / 100f; // 스케일 맞추기
        float fatScore = food.getSaturatedFat() * 2f;

        if (sugarScore >= sodiumScore && sugarScore >= fatScore) {
            return "sweet";
        } else if (sodiumScore >= fatScore) {
            return "salty";
        } else {
            return "greasy";
        }
    }

    /**
     * 몬스터 티어 결정
     */
    private String determineTier(float dangerScore, float calories) {
        float combinedScore = dangerScore + (calories * 0.02f);

        if (combinedScore >= 80) return "legendary";  // 레이드 필요
        if (combinedScore >= 50) return "epic";
        if (combinedScore >= 25) return "rare";
        return "common";
    }

    /**
     * 몬스터 이름 생성
     */
    private String generateMonsterName(String foodName, String element, String tier) {
        String prefix = "";
        String suffix = "";

        // 티어별 접두사
        switch (tier) {
            case "legendary": prefix = "고대의 "; break;
            case "epic": prefix = "분노한 "; break;
            case "rare": prefix = "거대한 "; break;
        }

        // 속성별 접미사
        switch (element) {
            case "sweet": suffix = " 슬라임"; break;
            case "salty": suffix = " 골렘"; break;
            case "greasy": suffix = " 드래곤"; break;
            case "toxic": suffix = " 악마"; break;
            default: suffix = " 몬스터"; break;
        }

        return prefix + foodName + suffix;
    }

    /**
     * 아이템 생성
     */
    private Item generateItem(NutritionData food, float healthScore, String ownerId) {
        Item item = new Item();

        item.setId(UUID.randomUUID().toString());
        item.setOwnerId(ownerId);
        item.setFoodName(food.getFoodName());
        item.setBarcode(food.getBarcode());
        item.setObtainedAt(System.currentTimeMillis());
        item.setQuantity(1);

        // 타입 결정: 단백질 우세 → 무기, 식이섬유 우세 → 포션
        boolean isWeaponType = food.getProtein() > food.getFiber();

        // 영양밀도가 높으면 버프 아이템
        float nutritionDensity = calculateNutritionDensity(food);
        if (nutritionDensity > 15) {
            item.setType(Item.ItemType.BUFF);
            item.setName(food.getFoodName() + " 에너지");
            item.setBuffPower(Math.round(nutritionDensity));
            item.setBuffDuration(30); // 30초
        } else if (isWeaponType) {
            item.setType(Item.ItemType.WEAPON);
            item.setName(food.getFoodName() + " 소드");
            int attackPower = Math.round(food.getProtein() * PROTEIN_TO_ATTACK);
            item.setAttackPower(Math.max(5, Math.min(attackPower, 100)));
        } else {
            item.setType(Item.ItemType.POTION);
            item.setName(food.getFoodName() + " 포션");
            int healAmount = Math.round(food.getFiber() * FIBER_TO_HEAL);
            item.setHealAmount(Math.max(5, Math.min(healAmount, 50)));
        }

        // 등급 결정
        item.setRarity(determineItemRarity(healthScore));

        return item;
    }

    /**
     * 아이템 등급 결정
     */
    private String determineItemRarity(float healthScore) {
        if (healthScore >= 60) return "legendary";
        if (healthScore >= 40) return "epic";
        if (healthScore >= 20) return "rare";
        return "common";
    }
}
