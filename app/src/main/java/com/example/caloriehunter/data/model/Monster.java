package com.example.caloriehunter.data.model;

import com.google.firebase.database.Exclude;
import java.util.HashMap;
import java.util.Map;

/**
 * 몬스터 데이터 모델
 * 불건강한 음식에서 생성되는 적 캐릭터
 */
public class Monster {

    private String id;
    private String ownerId;
    private String name;

    // 원본 음식 정보
    private String foodName;
    private String barcode;

    // 스탯
    private int hp;
    private int maxHp;
    private int defense;
    private int attack;
    private int poisonDamage;  // 트랜스지방 기반 지속 피해

    // 속성 및 등급
    private String element;    // sweet, salty, greasy, toxic
    private String tier;       // common, rare, epic, legendary

    // 상태
    private String status;     // active, defeated, expired
    private long createdAt;
    private long expiresAt;

    // 원본 영양 데이터 (참고용)
    private float originalSugar;
    private float originalSodium;
    private float originalSatFat;
    private float originalTransFat;
    private float originalCalories;

    public Monster() {
        // Firebase 기본 생성자
    }

    // Firebase에 저장할 Map 변환
    @Exclude
    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("ownerId", ownerId);
        result.put("name", name);
        result.put("foodName", foodName);
        result.put("barcode", barcode);
        result.put("hp", hp);
        result.put("maxHp", maxHp);
        result.put("defense", defense);
        result.put("attack", attack);
        result.put("poisonDamage", poisonDamage);
        result.put("element", element);
        result.put("tier", tier);
        result.put("status", status);
        result.put("createdAt", createdAt);
        result.put("expiresAt", expiresAt);
        result.put("originalSugar", originalSugar);
        result.put("originalSodium", originalSodium);
        result.put("originalSatFat", originalSatFat);
        result.put("originalTransFat", originalTransFat);
        result.put("originalCalories", originalCalories);
        return result;
    }

    // 티어별 색상 코드
    @Exclude
    public String getTierColor() {
        switch (tier) {
            case "legendary": return "#FFD700"; // 금색
            case "epic": return "#A855F7";      // 보라색
            case "rare": return "#3B82F6";      // 파란색
            default: return "#9CA3AF";          // 회색
        }
    }

    // 속성별 이모지
    @Exclude
    public String getElementEmoji() {
        switch (element) {
            case "sweet": return "🍭";
            case "salty": return "🧂";
            case "greasy": return "🛢️";
            case "toxic": return "☠️";
            default: return "👾";
        }
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFoodName() { return foodName; }
    public void setFoodName(String foodName) { this.foodName = foodName; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = hp; }

    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }

    public int getDefense() { return defense; }
    public void setDefense(int defense) { this.defense = defense; }

    public int getAttack() { return attack; }
    public void setAttack(int attack) { this.attack = attack; }

    public int getPoisonDamage() { return poisonDamage; }
    public void setPoisonDamage(int poisonDamage) { this.poisonDamage = poisonDamage; }

    public String getElement() { return element; }
    public void setElement(String element) { this.element = element; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }

    public float getOriginalSugar() { return originalSugar; }
    public void setOriginalSugar(float originalSugar) { this.originalSugar = originalSugar; }

    public float getOriginalSodium() { return originalSodium; }
    public void setOriginalSodium(float originalSodium) { this.originalSodium = originalSodium; }

    public float getOriginalSatFat() { return originalSatFat; }
    public void setOriginalSatFat(float originalSatFat) { this.originalSatFat = originalSatFat; }

    public float getOriginalTransFat() { return originalTransFat; }
    public void setOriginalTransFat(float originalTransFat) { this.originalTransFat = originalTransFat; }

    public float getOriginalCalories() { return originalCalories; }
    public void setOriginalCalories(float originalCalories) { this.originalCalories = originalCalories; }
}
