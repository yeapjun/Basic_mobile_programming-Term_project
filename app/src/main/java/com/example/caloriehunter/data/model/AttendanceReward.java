package com.example.caloriehunter.data.model;

import com.google.firebase.database.Exclude;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 출석 보상 모델
 * 매일 출석 체크 및 연속 출석 보상
 */
public class AttendanceReward {

    // 보상 타입
    public static final String REWARD_EXP = "EXP";
    public static final String REWARD_POTION = "POTION";
    public static final String REWARD_SPECIAL = "SPECIAL";

    private String oderId;
    private String date;              // "2025-12-19" 형식
    private int consecutiveDays;      // 연속 출석 일수
    private int totalDays;            // 총 출석 일수
    private String rewardType;        // 보상 타입
    private int rewardAmount;         // 보상 양
    private String rewardDescription; // 보상 설명
    private long claimedAt;           // 수령 시간

    public AttendanceReward() {
        // Firebase 기본 생성자
    }

    /**
     * 오늘의 출석 보상 생성
     */
    @Exclude
    public static AttendanceReward createTodayReward(String oderId, int consecutiveDays, int totalDays) {
        AttendanceReward reward = new AttendanceReward();
        reward.oderId = oderId;
        reward.date = getTodayDateString();
        reward.consecutiveDays = consecutiveDays;
        reward.totalDays = totalDays;
        reward.claimedAt = System.currentTimeMillis();

        // 연속 출석에 따른 보상 결정
        RewardInfo info = calculateReward(consecutiveDays);
        reward.rewardType = info.type;
        reward.rewardAmount = info.amount;
        reward.rewardDescription = info.description;

        return reward;
    }

    /**
     * 연속 출석에 따른 보상 계산
     */
    @Exclude
    public static RewardInfo calculateReward(int consecutiveDays) {
        RewardInfo info = new RewardInfo();

        if (consecutiveDays % 7 == 0) {
            // 7일마다 특별 보상 (포션)
            info.type = REWARD_POTION;
            info.amount = 1 + (consecutiveDays / 7); // 7일: 1개, 14일: 2개...
            info.description = "🧪 회복 포션 x" + info.amount;
            info.emoji = "🎁";
        } else if (consecutiveDays % 3 == 0) {
            // 3일마다 보너스 EXP
            info.type = REWARD_EXP;
            info.amount = 50 + (consecutiveDays * 5);
            info.description = "✨ 보너스 경험치 +" + info.amount;
            info.emoji = "⭐";
        } else {
            // 기본 EXP 보상
            info.type = REWARD_EXP;
            info.amount = 20 + (consecutiveDays * 2);
            info.description = "경험치 +" + info.amount;
            info.emoji = "📅";
        }

        return info;
    }

    /**
     * 보상 정보 클래스
     */
    public static class RewardInfo {
        public String type;
        public int amount;
        public String description;
        public String emoji;
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
     * 어제 날짜 문자열
     */
    @Exclude
    public static String getYesterdayDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        return sdf.format(cal.getTime());
    }

    /**
     * 이번 주 날짜 목록 (월~일)
     */
    @Exclude
    public static List<String> getThisWeekDates() {
        List<String> dates = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        Calendar cal = Calendar.getInstance();
        // 이번 주 월요일로 이동
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);

        for (int i = 0; i < 7; i++) {
            dates.add(sdf.format(cal.getTime()));
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        return dates;
    }

    /**
     * 날짜에서 요일 이름 얻기
     */
    @Exclude
    public static String getDayName(String dateString) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date date = sdf.parse(dateString);
            SimpleDateFormat dayFormat = new SimpleDateFormat("E", Locale.KOREAN);
            return dayFormat.format(date);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 날짜에서 일(day) 얻기
     */
    @Exclude
    public static String getDayNumber(String dateString) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date date = sdf.parse(dateString);
            SimpleDateFormat dayFormat = new SimpleDateFormat("d", Locale.getDefault());
            return dayFormat.format(date);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Firebase 저장용 Map
     */
    @Exclude
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("oderId", oderId);
        map.put("date", date);
        map.put("consecutiveDays", consecutiveDays);
        map.put("totalDays", totalDays);
        map.put("rewardType", rewardType);
        map.put("rewardAmount", rewardAmount);
        map.put("rewardDescription", rewardDescription);
        map.put("claimedAt", claimedAt);
        return map;
    }

    // Getters and Setters
    public String getOderId() { return oderId; }
    public void setOderId(String oderId) { this.oderId = oderId; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public int getConsecutiveDays() { return consecutiveDays; }
    public void setConsecutiveDays(int consecutiveDays) { this.consecutiveDays = consecutiveDays; }

    public int getTotalDays() { return totalDays; }
    public void setTotalDays(int totalDays) { this.totalDays = totalDays; }

    public String getRewardType() { return rewardType; }
    public void setRewardType(String rewardType) { this.rewardType = rewardType; }

    public int getRewardAmount() { return rewardAmount; }
    public void setRewardAmount(int rewardAmount) { this.rewardAmount = rewardAmount; }

    public String getRewardDescription() { return rewardDescription; }
    public void setRewardDescription(String rewardDescription) { this.rewardDescription = rewardDescription; }

    public long getClaimedAt() { return claimedAt; }
    public void setClaimedAt(long claimedAt) { this.claimedAt = claimedAt; }
}
