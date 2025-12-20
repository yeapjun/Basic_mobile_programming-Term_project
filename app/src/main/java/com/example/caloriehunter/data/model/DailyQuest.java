package com.example.caloriehunter.data.model;

import com.google.firebase.database.Exclude;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * 일일 퀘스트 모델
 */
public class DailyQuest {

    public enum QuestType {
        SCAN_FOOD,          // 음식 스캔하기
        SCAN_HEALTHY,       // 건강한 음식 스캔하기
        DEFEAT_MONSTER,     // 몬스터 처치하기
        USE_POTION,         // 포션 사용하기
        DEAL_DAMAGE         // 총 데미지 입히기
    }

    private String id;
    private String oderId;
    private String questType;
    private String title;
    private String description;
    private String emoji;
    private int targetCount;
    private int currentProgress;
    private int rewardExp;
    private boolean completed;
    private boolean rewardClaimed;
    private String date;  // "2025-12-18" 형식
    private long createdAt;

    public DailyQuest() {
        // Firebase 기본 생성자
    }

    /**
     * 오늘 날짜 문자열 반환
     */
    @Exclude
    public static String getTodayDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date());
    }

    /**
     * 랜덤 일일 퀘스트 3개 생성
     */
    @Exclude
    public static DailyQuest[] generateDailyQuests(String oderId) {
        DailyQuest[] quests = new DailyQuest[3];
        String today = getTodayDateString();
        Random random = new Random();

        // 퀘스트 1: 음식 스캔
        quests[0] = new DailyQuest();
        quests[0].id = "quest_scan_" + today;
        quests[0].oderId = oderId;
        quests[0].questType = QuestType.SCAN_FOOD.name();
        quests[0].title = "음식 스캔하기";
        quests[0].description = "아무 음식이나 스캔하세요";
        quests[0].emoji = "📷";
        quests[0].targetCount = 2 + random.nextInt(2);  // 2~3개
        quests[0].currentProgress = 0;
        quests[0].rewardExp = quests[0].targetCount * 15;
        quests[0].completed = false;
        quests[0].rewardClaimed = false;
        quests[0].date = today;
        quests[0].createdAt = System.currentTimeMillis();

        // 퀘스트 2: 건강한 음식 스캔
        quests[1] = new DailyQuest();
        quests[1].id = "quest_healthy_" + today;
        quests[1].oderId = oderId;
        quests[1].questType = QuestType.SCAN_HEALTHY.name();
        quests[1].title = "건강한 음식 스캔";
        quests[1].description = "건강한 음식을 스캔하여 무기나 포션을 획득하세요";
        quests[1].emoji = "🥗";
        quests[1].targetCount = 1 + random.nextInt(2);  // 1~2개
        quests[1].currentProgress = 0;
        quests[1].rewardExp = quests[1].targetCount * 25;
        quests[1].completed = false;
        quests[1].rewardClaimed = false;
        quests[1].date = today;
        quests[1].createdAt = System.currentTimeMillis();

        // 퀘스트 3: 몬스터 처치
        quests[2] = new DailyQuest();
        quests[2].id = "quest_defeat_" + today;
        quests[2].oderId = oderId;
        quests[2].questType = QuestType.DEFEAT_MONSTER.name();
        quests[2].title = "몬스터 처치";
        quests[2].description = "음식 몬스터를 처치하세요";
        quests[2].emoji = "⚔️";
        quests[2].targetCount = 1 + random.nextInt(2);  // 1~2마리
        quests[2].currentProgress = 0;
        quests[2].rewardExp = quests[2].targetCount * 30;
        quests[2].completed = false;
        quests[2].rewardClaimed = false;
        quests[2].date = today;
        quests[2].createdAt = System.currentTimeMillis();

        return quests;
    }

    /**
     * 진행도 증가
     */
    @Exclude
    public boolean addProgress(int amount) {
        if (completed) return false;

        currentProgress = Math.min(currentProgress + amount, targetCount);
        if (currentProgress >= targetCount) {
            completed = true;
            return true;  // 완료됨
        }
        return false;
    }

    /**
     * 진행률 퍼센트
     */
    @Exclude
    public int getProgressPercent() {
        if (targetCount <= 0) return 0;
        return (int) ((float) currentProgress / targetCount * 100);
    }

    /**
     * Firebase 저장용 Map
     */
    @Exclude
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("oderId", oderId);
        map.put("questType", questType);
        map.put("title", title);
        map.put("description", description);
        map.put("emoji", emoji);
        map.put("targetCount", targetCount);
        map.put("currentProgress", currentProgress);
        map.put("rewardExp", rewardExp);
        map.put("completed", completed);
        map.put("rewardClaimed", rewardClaimed);
        map.put("date", date);
        map.put("createdAt", createdAt);
        return map;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOderId() { return oderId; }
    public void setOderId(String oderId) { this.oderId = oderId; }

    public String getQuestType() { return questType; }
    public void setQuestType(String questType) { this.questType = questType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }

    public int getTargetCount() { return targetCount; }
    public void setTargetCount(int targetCount) { this.targetCount = targetCount; }

    public int getCurrentProgress() { return currentProgress; }
    public void setCurrentProgress(int currentProgress) { this.currentProgress = currentProgress; }

    public int getRewardExp() { return rewardExp; }
    public void setRewardExp(int rewardExp) { this.rewardExp = rewardExp; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public boolean isRewardClaimed() { return rewardClaimed; }
    public void setRewardClaimed(boolean rewardClaimed) { this.rewardClaimed = rewardClaimed; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
