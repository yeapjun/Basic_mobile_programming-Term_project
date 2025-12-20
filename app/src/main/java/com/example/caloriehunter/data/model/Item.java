package com.example.caloriehunter.data.model;

import com.google.firebase.database.Exclude;
import java.util.HashMap;
import java.util.Map;

/**
 * 아이템 데이터 모델
 * 건강한 음식에서 생성되는 무기/포션
 */
public class Item {

    public enum ItemType {
        WEAPON,     // 무기 (단백질 기반)
        POTION,     // 포션 (식이섬유 기반)
        BUFF        // 버프 아이템 (영양밀도 기반)
    }

    private String id;
    private String ownerId;
    private String name;
    private ItemType type;

    // 원본 음식 정보
    private String foodName;
    private String barcode;

    // 스탯
    private int attackPower;    // 무기 공격력
    private int healAmount;     // 포션 회복량
    private int buffDuration;   // 버프 지속 시간 (초)
    private int buffPower;      // 버프 효과량

    // 무기 내구도
    private int durability;     // 현재 내구도
    private int maxDurability;  // 최대 내구도

    // 등급 및 수량
    private String rarity;      // common, rare, epic, legendary
    private int quantity;

    // 메타데이터
    private long obtainedAt;

    public Item() {
        // Firebase 기본 생성자
        this.quantity = 1;
    }

    // Firebase에 저장할 Map 변환
    @Exclude
    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("ownerId", ownerId);
        result.put("name", name);
        result.put("type", type != null ? type.name() : null);
        result.put("foodName", foodName);
        result.put("barcode", barcode);
        result.put("attackPower", attackPower);
        result.put("healAmount", healAmount);
        result.put("buffDuration", buffDuration);
        result.put("buffPower", buffPower);
        result.put("durability", durability);
        result.put("maxDurability", maxDurability);
        result.put("rarity", rarity);
        result.put("quantity", quantity);
        result.put("obtainedAt", obtainedAt);
        return result;
    }

    // 등급별 색상 코드
    @Exclude
    public String getRarityColor() {
        switch (rarity) {
            case "legendary": return "#FFD700"; // 금색
            case "epic": return "#A855F7";      // 보라색
            case "rare": return "#3B82F6";      // 파란색
            default: return "#9CA3AF";          // 회색
        }
    }

    // 타입별 이모지
    @Exclude
    public String getTypeEmoji() {
        if (type == null) return "📦";
        switch (type) {
            case WEAPON: return "⚔️";
            case POTION: return "💚";
            case BUFF: return "⚡";
            default: return "📦";
        }
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ItemType getType() { return type; }
    public void setType(ItemType type) { this.type = type; }

    public String getFoodName() { return foodName; }
    public void setFoodName(String foodName) { this.foodName = foodName; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public int getAttackPower() { return attackPower; }
    public void setAttackPower(int attackPower) { this.attackPower = attackPower; }

    public int getHealAmount() { return healAmount; }
    public void setHealAmount(int healAmount) { this.healAmount = healAmount; }

    public int getBuffDuration() { return buffDuration; }
    public void setBuffDuration(int buffDuration) { this.buffDuration = buffDuration; }

    public int getBuffPower() { return buffPower; }
    public void setBuffPower(int buffPower) { this.buffPower = buffPower; }

    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public long getObtainedAt() { return obtainedAt; }
    public void setObtainedAt(long obtainedAt) { this.obtainedAt = obtainedAt; }

    public int getDurability() { return durability; }
    public void setDurability(int durability) { this.durability = durability; }

    public int getMaxDurability() { return maxDurability; }
    public void setMaxDurability(int maxDurability) { this.maxDurability = maxDurability; }

    // 내구도 감소
    @Exclude
    public boolean reduceDurability(int amount) {
        this.durability = Math.max(0, this.durability - amount);
        return this.durability <= 0; // true면 파괴됨
    }

    // 내구도 퍼센트
    @Exclude
    public int getDurabilityPercent() {
        if (maxDurability <= 0) return 100;
        return (int) ((float) durability / maxDurability * 100);
    }

    /**
     * 출석 보상용 포션 생성
     */
    @Exclude
    public static Item createAttendancePotion(String ownerId) {
        Item potion = new Item();
        potion.setId(java.util.UUID.randomUUID().toString());
        potion.setOwnerId(ownerId);
        potion.setName("출석 포션");
        potion.setType(ItemType.POTION);
        potion.setFoodName("출석 보상");
        potion.setHealAmount(30);  // 30 HP 회복
        potion.setRarity("rare");
        potion.setQuantity(1);
        potion.setObtainedAt(System.currentTimeMillis());
        return potion;
    }
}
