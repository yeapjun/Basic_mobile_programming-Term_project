package com.example.caloriehunter.data.model;

import com.google.firebase.database.Exclude;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 사용자 데이터 모델
 */
public class User {

    private String uid;
    private String nickname;

    // 레벨 시스템
    private int level;
    private int exp;
    private int expToNextLevel;

    // 유저 스탯
    private int hp;
    private int maxHp;

    // 전적
    private int totalMonstersKilled;
    private int totalDamageDealt;
    private int healthyFoodCount;
    private int unhealthyFoodCount;

    // 몬스터 대기열 (연속으로 나쁜 음식을 먹으면 몬스터가 쌓임)
    private List<String> monsterQueue;

    // 장착 아이템
    private String equippedWeaponId;
    private String equippedWeaponName;
    private int equippedWeaponPower;

    // 메타데이터
    private long createdAt;
    private long lastLoginAt;

    public User() {
        // Firebase 기본 생성자
        this.level = 1;
        this.exp = 0;
        this.expToNextLevel = 100;
        this.hp = 100;
        this.maxHp = 100;
        this.monsterQueue = new ArrayList<>();
    }

    // 새 유저 생성
    public static User createNewUser(String uid, String nickname) {
        User user = new User();
        user.uid = uid;
        user.nickname = nickname;
        user.createdAt = System.currentTimeMillis();
        user.lastLoginAt = System.currentTimeMillis();
        return user;
    }

    // 경험치 획득 및 레벨업 체크
    @Exclude
    public boolean addExp(int amount) {
        this.exp += amount;
        if (this.exp >= this.expToNextLevel) {
            levelUp();
            return true;
        }
        return false;
    }

    private void levelUp() {
        this.level++;
        this.exp -= this.expToNextLevel;
        this.expToNextLevel = calculateExpForLevel(this.level + 1);
        this.maxHp += 10; // 레벨업 시 최대 HP 증가
        this.hp = this.maxHp; // 풀 회복
    }

    private int calculateExpForLevel(int level) {
        // 레벨이 오를수록 필요 경험치 증가
        return (int) (100 * Math.pow(1.2, level - 1));
    }

    // HP 관리
    @Exclude
    public void takeDamage(int damage) {
        this.hp = Math.max(0, this.hp - damage);
    }

    @Exclude
    public void heal(int amount) {
        this.hp = Math.min(this.maxHp, this.hp + amount);
    }

    @Exclude
    public boolean isAlive() {
        return this.hp > 0;
    }

    // Firebase에 저장할 Map 변환
    @Exclude
    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("uid", uid);
        result.put("nickname", nickname);
        result.put("level", level);
        result.put("exp", exp);
        result.put("expToNextLevel", expToNextLevel);
        result.put("hp", hp);
        result.put("maxHp", maxHp);
        result.put("totalMonstersKilled", totalMonstersKilled);
        result.put("totalDamageDealt", totalDamageDealt);
        result.put("healthyFoodCount", healthyFoodCount);
        result.put("unhealthyFoodCount", unhealthyFoodCount);
        result.put("monsterQueue", monsterQueue);
        result.put("equippedWeaponId", equippedWeaponId);
        result.put("equippedWeaponName", equippedWeaponName);
        result.put("equippedWeaponPower", equippedWeaponPower);
        result.put("createdAt", createdAt);
        result.put("lastLoginAt", lastLoginAt);
        return result;
    }

    // Getters & Setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getExp() { return exp; }
    public void setExp(int exp) { this.exp = exp; }

    public int getExpToNextLevel() { return expToNextLevel; }
    public void setExpToNextLevel(int expToNextLevel) { this.expToNextLevel = expToNextLevel; }

    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = hp; }

    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }

    public int getTotalMonstersKilled() { return totalMonstersKilled; }
    public void setTotalMonstersKilled(int totalMonstersKilled) { this.totalMonstersKilled = totalMonstersKilled; }

    public int getTotalDamageDealt() { return totalDamageDealt; }
    public void setTotalDamageDealt(int totalDamageDealt) { this.totalDamageDealt = totalDamageDealt; }

    public int getHealthyFoodCount() { return healthyFoodCount; }
    public void setHealthyFoodCount(int healthyFoodCount) { this.healthyFoodCount = healthyFoodCount; }

    public int getUnhealthyFoodCount() { return unhealthyFoodCount; }
    public void setUnhealthyFoodCount(int unhealthyFoodCount) { this.unhealthyFoodCount = unhealthyFoodCount; }

    // 몬스터 대기열 관련 메서드
    public List<String> getMonsterQueue() {
        if (monsterQueue == null) {
            monsterQueue = new ArrayList<>();
        }
        return monsterQueue;
    }
    public void setMonsterQueue(List<String> monsterQueue) {
        this.monsterQueue = monsterQueue != null ? monsterQueue : new ArrayList<>();
    }

    // 호환성을 위한 메서드 (첫 번째 몬스터 반환)
    @Exclude
    public String getActiveMonsterId() {
        if (monsterQueue == null || monsterQueue.isEmpty()) {
            return null;
        }
        return monsterQueue.get(0);
    }

    // 대기열에 몬스터 추가
    @Exclude
    public void addMonsterToQueue(String monsterId) {
        if (monsterQueue == null) {
            monsterQueue = new ArrayList<>();
        }
        monsterQueue.add(monsterId);
    }

    // 첫 번째 몬스터 제거 (전투 승리 시)
    @Exclude
    public String removeFirstMonster() {
        if (monsterQueue == null || monsterQueue.isEmpty()) {
            return null;
        }
        return monsterQueue.remove(0);
    }

    // 대기열에 몬스터가 있는지 확인
    @Exclude
    public boolean hasActiveMonster() {
        return monsterQueue != null && !monsterQueue.isEmpty();
    }

    // 대기열 크기 반환
    @Exclude
    public int getMonsterQueueSize() {
        return monsterQueue != null ? monsterQueue.size() : 0;
    }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(long lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public String getEquippedWeaponId() { return equippedWeaponId; }
    public void setEquippedWeaponId(String equippedWeaponId) { this.equippedWeaponId = equippedWeaponId; }

    public String getEquippedWeaponName() { return equippedWeaponName; }
    public void setEquippedWeaponName(String equippedWeaponName) { this.equippedWeaponName = equippedWeaponName; }

    public int getEquippedWeaponPower() { return equippedWeaponPower; }
    public void setEquippedWeaponPower(int equippedWeaponPower) { this.equippedWeaponPower = equippedWeaponPower; }

    // 무기 장착
    @Exclude
    public void equipWeapon(Item weapon) {
        if (weapon != null && weapon.getType() == Item.ItemType.WEAPON) {
            this.equippedWeaponId = weapon.getId();
            this.equippedWeaponName = weapon.getName();
            this.equippedWeaponPower = weapon.getAttackPower();
        }
    }

    // 무기 해제
    @Exclude
    public void unequipWeapon() {
        this.equippedWeaponId = null;
        this.equippedWeaponName = null;
        this.equippedWeaponPower = 0;
    }

    // 총 공격력 계산
    @Exclude
    public int getTotalAttackPower() {
        int baseAttack = 10 + level * 2;
        return baseAttack + equippedWeaponPower;
    }
}
