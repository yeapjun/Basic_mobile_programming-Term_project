package com.example.caloriehunter.data.model;

import com.google.firebase.database.Exclude;
import java.util.HashMap;
import java.util.Map;

/**
 * 전투 기록 모델
 * 처치한 몬스터 정보를 저장
 */
public class BattleLog {

    private String id;
    private String ownerId;

    // 몬스터 정보
    private String monsterName;
    private String monsterTier;
    private String monsterElement;
    private int monsterMaxHp;
    private String foodName;

    // 전투 결과
    private boolean victory;
    private int expGained;
    private int damageDealt;

    // 타임스탬프
    private long timestamp;

    public BattleLog() {
        // Firebase 기본 생성자
    }

    // 승리 로그 생성
    public static BattleLog createVictoryLog(String ownerId, Monster monster, int expGained) {
        BattleLog log = new BattleLog();
        log.id = java.util.UUID.randomUUID().toString();
        log.ownerId = ownerId;
        log.monsterName = monster.getName();
        log.monsterTier = monster.getTier();
        log.monsterElement = monster.getElement();
        log.monsterMaxHp = monster.getMaxHp();
        log.foodName = monster.getFoodName();
        log.victory = true;
        log.expGained = expGained;
        log.damageDealt = monster.getMaxHp();
        log.timestamp = System.currentTimeMillis();
        return log;
    }

    // Firebase에 저장할 Map 변환
    @Exclude
    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("ownerId", ownerId);
        result.put("monsterName", monsterName);
        result.put("monsterTier", monsterTier);
        result.put("monsterElement", monsterElement);
        result.put("monsterMaxHp", monsterMaxHp);
        result.put("foodName", foodName);
        result.put("victory", victory);
        result.put("expGained", expGained);
        result.put("damageDealt", damageDealt);
        result.put("timestamp", timestamp);
        return result;
    }

    // 티어별 색상 코드
    @Exclude
    public String getTierColor() {
        switch (monsterTier) {
            case "legendary": return "#FFD700";
            case "epic": return "#A855F7";
            case "rare": return "#3B82F6";
            default: return "#9CA3AF";
        }
    }

    // 속성별 이모지
    @Exclude
    public String getElementEmoji() {
        switch (monsterElement) {
            case "sweet": return "🍭";
            case "salty": return "🧂";
            case "greasy": return "🛢️";
            case "toxic": return "☠️";
            default: return "👾";
        }
    }

    // 시간 포맷팅 (몇 분 전, 몇 시간 전 등)
    @Exclude
    public String getTimeAgo() {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "일 전";
        if (hours > 0) return hours + "시간 전";
        if (minutes > 0) return minutes + "분 전";
        return "방금 전";
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getMonsterName() { return monsterName; }
    public void setMonsterName(String monsterName) { this.monsterName = monsterName; }

    public String getMonsterTier() { return monsterTier; }
    public void setMonsterTier(String monsterTier) { this.monsterTier = monsterTier; }

    public String getMonsterElement() { return monsterElement; }
    public void setMonsterElement(String monsterElement) { this.monsterElement = monsterElement; }

    public int getMonsterMaxHp() { return monsterMaxHp; }
    public void setMonsterMaxHp(int monsterMaxHp) { this.monsterMaxHp = monsterMaxHp; }

    public String getFoodName() { return foodName; }
    public void setFoodName(String foodName) { this.foodName = foodName; }

    public boolean isVictory() { return victory; }
    public void setVictory(boolean victory) { this.victory = victory; }

    public int getExpGained() { return expGained; }
    public void setExpGained(int expGained) { this.expGained = expGained; }

    public int getDamageDealt() { return damageDealt; }
    public void setDamageDealt(int damageDealt) { this.damageDealt = damageDealt; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
