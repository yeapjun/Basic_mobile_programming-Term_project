package com.example.caloriehunter.game;

import com.example.caloriehunter.data.model.Item;
import com.example.caloriehunter.data.model.Monster;
import com.example.caloriehunter.data.model.NutritionData;

import java.util.UUID;

/**
 * 음식 분석 엔진 v2.0
 * "직업과 타락 시스템" - 2단계 판정 로직
 *
 * 1단계: 신분 확인 (Class Determination) - 주요 영양소로 직업 결정
 * 2단계: 정밀 검사 (Corruption Check) - 나쁜 성분으로 오염도 계산
 * 3단계: 최종 판정 (Final Decision) - 깨끗하면 아이템, 오염되면 몬스터
 */
public class FoodAnalyzer {

    // ==================== 직업(Class) 정의 ====================
    /**
     * 음식의 "타고난 직업"
     * 주요 영양소에 따라 결정됨
     */
    public enum FoodClass {
        WARRIOR,    // 전사 - 단백질 위주
        MAGE,       // 마법사 - 탄수화물 위주
        BERSERKER,  // 광전사 - 지방 위주
        PRIEST      // 사제 - 식이섬유 위주
    }

    // ==================== 오염도 가중치 ====================
    private static final float SUGAR_PENALTY = 1.0f;        // 설탕: 1g당 1점
    private static final float SODIUM_PENALTY = 0.01f;      // 나트륨: 100mg당 1점 (0.01 * 100 = 1)
    private static final float SAT_FAT_PENALTY = 3.0f;      // 포화지방: 1g당 3점 (패스트푸드 주범)
    private static final float TRANS_FAT_PENALTY = 20.0f;   // 트랜스지방: 1g당 20점 (치명적)

    // ==================== 판정 임계값 ====================
    private static final float CORRUPTION_THRESHOLD = 20.0f;           // 오염도 기준치
    private static final float FAST_FOOD_RATIO_THRESHOLD = 2.0f;       // 단백질/포화지방 비율 기준
    private static final float MIN_SAT_FAT_FOR_FAST_FOOD_CHECK = 3.0f; // 패스트푸드 검사 최소 포화지방

    // ==================== 스탯 변환 계수 ====================
    private static final float PROTEIN_TO_ATTACK = 2.5f;
    private static final float CARBS_TO_MAGIC = 1.5f;
    private static final float FAT_TO_POWER = 3.0f;
    private static final float FIBER_TO_HEAL = 10.0f;
    private static final float CORRUPTION_TO_HP = 3.0f;
    private static final float CORRUPTION_TO_ATTACK = 1.5f;

    // ==================== 분석 결과 클래스 ====================
    /**
     * 분석 결과를 담는 클래스
     */
    public static class AnalysisResult {
        public enum ResultType { MONSTER, ITEM }

        private ResultType type;
        private Monster monster;
        private Item item;
        private FoodClass foodClass;
        private float corruptionScore;
        private boolean isFastFood;

        public ResultType getType() { return type; }
        public Monster getMonster() { return monster; }
        public Item getItem() { return item; }
        public FoodClass getFoodClass() { return foodClass; }
        public float getCorruptionScore() { return corruptionScore; }
        public boolean isFastFood() { return isFastFood; }
        public boolean isMonster() { return type == ResultType.MONSTER; }
    }

    // ==================== 메인 분석 함수 ====================
    /**
     * 메인 분석 함수
     * 영양 데이터를 받아 몬스터 또는 아이템 생성
     */
    public AnalysisResult analyze(NutritionData food, String ownerId) {
        AnalysisResult result = new AnalysisResult();

        // ========================================
        // 1단계: 신분 확인 (Class Determination)
        // 나쁜 성분은 무시하고, 주요 영양소로 직업 결정
        // ========================================
        result.foodClass = determineClass(food);

        // ========================================
        // 2단계: 정밀 검사 (Corruption Check)
        // 나쁜 성분 검사하여 오염도 계산
        // ========================================
        result.corruptionScore = calculateCorruptionScore(food);
        result.isFastFood = checkFastFood(food);

        // ========================================
        // 3단계: 최종 판정 (Final Decision)
        // 오염도가 기준치 초과 또는 패스트푸드 → 몬스터
        // 깨끗하면 → 아이템
        // ========================================
        boolean isCorrupted = result.corruptionScore > CORRUPTION_THRESHOLD || result.isFastFood;

        if (isCorrupted) {
            result.type = AnalysisResult.ResultType.MONSTER;
            result.monster = generateCorruptedMonster(food, result.foodClass, result.corruptionScore, ownerId);
        } else {
            result.type = AnalysisResult.ResultType.ITEM;
            result.item = generatePureItem(food, result.foodClass, ownerId);
        }

        return result;
    }

    // ==================== 1단계: 신분 확인 ====================
    /**
     * 1단계: 신분 확인 (Class Determination)
     * 나쁜 성분은 무시하고, 주요 영양소 기준으로 직업 결정
     *
     * @param food 영양 데이터
     * @return 음식의 직업
     */
    private FoodClass determineClass(NutritionData food) {
        // 각 영양소의 "존재감" 점수 계산
        // 단백질: g 그대로
        // 탄수화물: g 그대로 (당류 제외한 복합탄수화물 추정)
        // 지방: g * 2 (칼로리 밀도 고려)
        // 식이섬유: g * 3 (희귀성 고려)

        float proteinScore = food.getProtein();
        float carbsScore = Math.max(0, food.getCarbohydrates() - food.getSugar()); // 복합탄수화물만
        float fatScore = food.getFat() * 2;
        float fiberScore = food.getFiber() * 3;

        // 가장 높은 점수의 영양소가 직업 결정
        float maxScore = Math.max(Math.max(proteinScore, carbsScore), Math.max(fatScore, fiberScore));

        if (maxScore <= 0) {
            // 영양소가 거의 없는 경우 (물, 차 등) → 사제
            return FoodClass.PRIEST;
        }

        if (fiberScore == maxScore) {
            return FoodClass.PRIEST;      // 식이섬유 위주 → 사제
        } else if (proteinScore == maxScore) {
            return FoodClass.WARRIOR;     // 단백질 위주 → 전사
        } else if (carbsScore == maxScore) {
            return FoodClass.MAGE;        // 탄수화물 위주 → 마법사
        } else {
            return FoodClass.BERSERKER;   // 지방 위주 → 광전사
        }
    }

    // ==================== 2단계: 정밀 검사 ====================
    /**
     * 2단계-A: 오염도 계산 (Corruption Score)
     * 나쁜 성분에 가중치를 곱해 오염도 산출
     *
     * @param food 영양 데이터
     * @return 오염도 점수
     */
    private float calculateCorruptionScore(NutritionData food) {
        float score = 0;

        // 설탕: 1g당 1점
        score += food.getSugar() * SUGAR_PENALTY;

        // 나트륨: 100mg당 1점
        score += food.getSodium() * SODIUM_PENALTY;

        // 포화지방: 1g당 3점 (패스트푸드 주범이므로 높게 책정)
        score += food.getSaturatedFat() * SAT_FAT_PENALTY;

        // 트랜스지방: 1g당 20점 (치명적)
        score += food.getTransFat() * TRANS_FAT_PENALTY;

        return score;
    }

    /**
     * 2단계-B: 패스트푸드 판별
     * 단백질 대비 포화지방 비율로 패스트푸드 여부 판단
     * (치킨, 햄버거 등 단백질은 높지만 지방도 높은 음식 걸러내기)
     *
     * @param food 영양 데이터
     * @return 패스트푸드 여부
     */
    private boolean checkFastFood(NutritionData food) {
        // 포화지방이 최소 기준 이상일 때만 검사
        if (food.getSaturatedFat() < MIN_SAT_FAT_FOR_FAST_FOOD_CHECK) {
            return false;
        }

        // 단백질 / 포화지방 비율 계산
        // 비율이 2.0 미만이면 패스트푸드로 간주
        // 예: 단백질 20g, 포화지방 15g → 비율 1.33 → 패스트푸드!
        // 예: 단백질 30g, 포화지방 5g → 비율 6.0 → 건강한 음식
        float ratio = food.getProtein() / food.getSaturatedFat();

        return ratio < FAST_FOOD_RATIO_THRESHOLD;
    }

    // ==================== 3단계: 몬스터 생성 ====================
    /**
     * 3단계-A: 타락한 몬스터 생성
     * 직업에 따라 다른 유형의 타락한 몬스터 생성
     *
     * @param food 영양 데이터
     * @param foodClass 음식의 직업
     * @param corruptionScore 오염도
     * @param ownerId 소유자 ID
     * @return 생성된 몬스터
     */
    private Monster generateCorruptedMonster(NutritionData food, FoodClass foodClass,
                                              float corruptionScore, String ownerId) {
        Monster monster = new Monster();

        monster.setId(UUID.randomUUID().toString());
        monster.setOwnerId(ownerId);
        monster.setFoodName(food.getFoodName());
        monster.setBarcode(food.getBarcode());

        // 직업별 속성 결정
        monster.setElement(getCorruptedElement(foodClass));

        // 오염도 기반 티어 결정
        monster.setTier(determineMonsterTier(corruptionScore));

        // 직업별 타락 이름 생성
        monster.setName(generateCorruptedName(food.getFoodName(), foodClass, monster.getTier()));

        // 스탯 계산 (오염도 + 직업 기반)
        calculateMonsterStats(monster, food, foodClass, corruptionScore);

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
     * 직업별 타락 속성 결정
     */
    private String getCorruptedElement(FoodClass foodClass) {
        switch (foodClass) {
            case WARRIOR:
                return "rage";      // 분노 (타락한 전사)
            case MAGE:
                return "chaos";     // 혼돈 (타락한 마법사)
            case BERSERKER:
                return "greasy";    // 기름 (타락한 광전사)
            case PRIEST:
                return "toxic";     // 독 (부패한 사제 - 탕후루 등)
            default:
                return "dark";
        }
    }

    /**
     * 몬스터 티어 결정 (오염도 기반)
     */
    private String determineMonsterTier(float corruptionScore) {
        if (corruptionScore >= 80) return "legendary";  // 극악
        if (corruptionScore >= 50) return "epic";       // 매우 나쁨
        if (corruptionScore >= 30) return "rare";       // 나쁨
        return "common";                                 // 약간 나쁨
    }

    /**
     * 타락한 몬스터 이름 생성
     */
    private String generateCorruptedName(String foodName, FoodClass foodClass, String tier) {
        String prefix = "";
        String classTitle = "";

        // 티어별 접두사
        switch (tier) {
            case "legendary": prefix = "대악마 "; break;
            case "epic": prefix = "암흑 "; break;
            case "rare": prefix = "타락한 "; break;
            default: prefix = ""; break;
        }

        // 직업별 타락 명칭
        switch (foodClass) {
            case WARRIOR:
                classTitle = "전사";
                break;
            case MAGE:
                classTitle = "마법사";
                break;
            case BERSERKER:
                classTitle = "광전사";
                break;
            case PRIEST:
                classTitle = "사제";  // 부패한 사제
                if (tier.equals("common") || tier.equals("rare")) {
                    prefix = "부패한 ";
                }
                break;
        }

        return prefix + foodName + " " + classTitle;
    }

    /**
     * 몬스터 스탯 계산
     */
    private void calculateMonsterStats(Monster monster, NutritionData food,
                                        FoodClass foodClass, float corruptionScore) {
        // 기본 HP: 오염도 기반
        int baseHp = Math.round(corruptionScore * CORRUPTION_TO_HP);
        int calBonus = Math.round(food.getCalories() * 0.03f);
        monster.setMaxHp(Math.max(30, Math.min(baseHp + calBonus, 500)));
        monster.setHp(monster.getMaxHp());

        // 기본 공격력: 오염도 기반
        int baseAttack = Math.round(corruptionScore * CORRUPTION_TO_ATTACK);

        // 직업별 추가 스탯
        switch (foodClass) {
            case WARRIOR:
                // 타락한 전사: 높은 공격력
                baseAttack += Math.round(food.getProtein() * 1.0f);
                monster.setDefense(Math.round(food.getSodium() * 0.01f));
                break;
            case MAGE:
                // 타락한 마법사: 특수 효과 (독 데미지)
                monster.setPoisonDamage(Math.round(food.getSugar() * 0.3f));
                break;
            case BERSERKER:
                // 타락한 광전사: 최고 공격력, 낮은 방어력
                baseAttack += Math.round(food.getSaturatedFat() * 2.0f);
                break;
            case PRIEST:
                // 부패한 사제: 독 데미지 특화
                monster.setPoisonDamage(Math.round(food.getSugar() * 0.5f + food.getTransFat() * 5.0f));
                break;
        }

        monster.setAttack(Math.max(5, Math.min(baseAttack, 80)));
        if (monster.getDefense() == 0) {
            monster.setDefense(Math.max(0, Math.min(Math.round(food.getSodium() * 0.015f), 50)));
        }
    }

    // ==================== 3단계: 아이템 생성 ====================
    /**
     * 3단계-B: 순수한 아이템 생성
     * 직업에 따라 다른 유형의 아이템 생성
     *
     * @param food 영양 데이터
     * @param foodClass 음식의 직업
     * @param ownerId 소유자 ID
     * @return 생성된 아이템
     */
    private Item generatePureItem(NutritionData food, FoodClass foodClass, String ownerId) {
        Item item = new Item();

        item.setId(UUID.randomUUID().toString());
        item.setOwnerId(ownerId);
        item.setFoodName(food.getFoodName());
        item.setBarcode(food.getBarcode());
        item.setObtainedAt(System.currentTimeMillis());
        item.setQuantity(1);

        // 직업별 아이템 타입 및 스탯 결정
        switch (foodClass) {
            case WARRIOR:
                // 전사 → 무기 (성기사의 검)
                generateWarriorItem(item, food);
                break;
            case MAGE:
                // 마법사 → 공격 버프 (마법의 두루마리)
                generateMageItem(item, food);
                break;
            case BERSERKER:
                // 광전사 → 강력한 무기 (광전사의 도끼)
                generateBerserkerItem(item, food);
                break;
            case PRIEST:
                // 사제 → 포션/힐링 (엘릭서)
                generatePriestItem(item, food);
                break;
        }

        // 등급 결정 (영양 밀도 기반)
        float purityScore = calculatePurityScore(food);
        item.setRarity(determineItemRarity(purityScore));

        return item;
    }

    /**
     * 전사 아이템 생성 (무기)
     */
    private void generateWarriorItem(Item item, NutritionData food) {
        item.setType(Item.ItemType.WEAPON);
        item.setName("성기사의 " + food.getFoodName());

        int attackPower = Math.round(food.getProtein() * PROTEIN_TO_ATTACK);
        item.setAttackPower(Math.max(15, Math.min(attackPower, 100)));

        // 내구도 (단백질 품질에 따라)
        int durability = Math.round(food.getProtein() * 0.8f);
        durability = Math.max(10, Math.min(durability, 30));
        item.setMaxDurability(durability);
        item.setDurability(durability);
    }

    /**
     * 마법사 아이템 생성 (공격 버프)
     */
    private void generateMageItem(Item item, NutritionData food) {
        item.setType(Item.ItemType.BUFF);
        item.setName(food.getFoodName() + " 마력");

        // 탄수화물 기반 공격력 버프 (20~80%)
        float carbsExcludingSugar = Math.max(0, food.getCarbohydrates() - food.getSugar());
        int buffPower = Math.round(carbsExcludingSugar * CARBS_TO_MAGIC);
        item.setBuffPower(Math.max(20, Math.min(buffPower, 80)));
    }

    /**
     * 광전사 아이템 생성 (강력한 무기)
     */
    private void generateBerserkerItem(Item item, NutritionData food) {
        item.setType(Item.ItemType.WEAPON);
        item.setName("광전사의 " + food.getFoodName());

        // 지방 기반 높은 공격력
        int attackPower = Math.round(food.getFat() * FAT_TO_POWER);
        item.setAttackPower(Math.max(20, Math.min(attackPower, 120)));

        // 내구도 낮음 (광전사는 격렬하게 싸움)
        item.setMaxDurability(8);
        item.setDurability(8);
    }

    /**
     * 사제 아이템 생성 (포션/수비 버프)
     */
    private void generatePriestItem(Item item, NutritionData food) {
        if (food.getFiber() >= 2) {
            // 식이섬유가 충분하면 → 포션 (엘릭서)
            item.setType(Item.ItemType.POTION);
            item.setName(food.getFoodName() + " 엘릭서");

            int healAmount = Math.round(food.getFiber() * FIBER_TO_HEAL);
            item.setHealAmount(Math.max(15, Math.min(healAmount, 80)));
        } else {
            // 식이섬유가 적으면 → 수비 버프 (축복)
            item.setType(Item.ItemType.BUFF);
            item.setName(food.getFoodName() + " 축복");

            // 가벼운 음식의 정화 효과 (수비 버프)
            item.setDefenseBoost(Math.max(15, Math.min(40, 30)));
        }
    }

    /**
     * 순수도 점수 계산 (높을수록 좋은 아이템)
     */
    private float calculatePurityScore(NutritionData food) {
        float score = 0;

        // 좋은 성분 점수
        score += food.getProtein() * 2.0f;
        score += food.getFiber() * 3.0f;
        score += Math.max(0, food.getCarbohydrates() - food.getSugar()) * 0.5f;

        // 나쁜 성분 감점 (오염도의 역)
        float corruption = calculateCorruptionScore(food);
        score -= corruption * 0.5f;

        return Math.max(0, score);
    }

    /**
     * 아이템 등급 결정
     */
    private String determineItemRarity(float purityScore) {
        if (purityScore >= 50) return "legendary";
        if (purityScore >= 35) return "epic";
        if (purityScore >= 20) return "rare";
        return "common";
    }
}
