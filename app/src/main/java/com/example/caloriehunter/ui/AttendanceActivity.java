package com.example.caloriehunter.ui;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.caloriehunter.R;
import com.example.caloriehunter.data.model.AttendanceReward;
import com.example.caloriehunter.data.repository.FirebaseRepository;
import com.example.caloriehunter.databinding.ActivityAttendanceBinding;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 출석 체크 화면
 * - 연속 출석 보상
 * - 주간 캘린더 표시
 */
public class AttendanceActivity extends AppCompatActivity {

    private ActivityAttendanceBinding binding;
    private FirebaseRepository firebaseRepository;
    private boolean isCheckedInToday = false;
    private int currentStreak = 0;
    private Set<String> checkedDates = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAttendanceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firebaseRepository = FirebaseRepository.getInstance();

        setupViews();
        loadAttendanceData();
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnCheckIn.setOnClickListener(v -> {
            if (!isCheckedInToday) {
                performCheckIn();
            } else {
                Toast.makeText(this, "오늘은 이미 출석했습니다!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadAttendanceData() {
        binding.loadingOverlay.setVisibility(View.VISIBLE);

        String userId = firebaseRepository.getCurrentUserId();
        if (userId == null) {
            Toast.makeText(this, "로그인이 필요합니다", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 오늘 출석 여부 확인
        firebaseRepository.getTodayAttendance(userId, new FirebaseRepository.AttendanceCallback() {
            @Override
            public void onSuccess(AttendanceReward reward) {
                // 이미 출석함
                isCheckedInToday = true;
                currentStreak = reward.getConsecutiveDays();
                runOnUiThread(() -> {
                    showTodayReward(reward);
                    updateStreakUI();
                    loadWeeklyCalendar(userId);
                });
            }

            @Override
            public void onError(String message) {
                // 오늘 출석 안함 - 어제 기록 확인
                isCheckedInToday = false;
                firebaseRepository.getLatestAttendance(userId, new FirebaseRepository.AttendanceCallback() {
                    @Override
                    public void onSuccess(AttendanceReward reward) {
                        // 어제 출석했으면 연속일 유지
                        String yesterday = AttendanceReward.getYesterdayDateString();
                        if (yesterday.equals(reward.getDate())) {
                            currentStreak = reward.getConsecutiveDays();
                        } else {
                            currentStreak = 0;
                        }
                        runOnUiThread(() -> {
                            updateStreakUI();
                            loadWeeklyCalendar(userId);
                        });
                    }

                    @Override
                    public void onError(String msg) {
                        currentStreak = 0;
                        runOnUiThread(() -> {
                            updateStreakUI();
                            loadWeeklyCalendar(userId);
                        });
                    }
                });
            }
        });
    }

    private void loadWeeklyCalendar(String userId) {
        firebaseRepository.getWeeklyAttendance(userId, new FirebaseRepository.AttendanceListCallback() {
            @Override
            public void onSuccess(List<AttendanceReward> rewards) {
                checkedDates.clear();
                for (AttendanceReward reward : rewards) {
                    checkedDates.add(reward.getDate());
                }
                runOnUiThread(() -> {
                    buildWeekCalendar();
                    binding.loadingOverlay.setVisibility(View.GONE);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    buildWeekCalendar();
                    binding.loadingOverlay.setVisibility(View.GONE);
                });
            }
        });
    }

    private void buildWeekCalendar() {
        binding.weekCalendar.removeAllViews();

        List<String> weekDates = AttendanceReward.getThisWeekDates();
        String today = AttendanceReward.getTodayDateString();

        for (String date : weekDates) {
            LinearLayout dayLayout = new LinearLayout(this);
            dayLayout.setOrientation(LinearLayout.VERTICAL);
            dayLayout.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            dayLayout.setLayoutParams(params);

            // 요일 텍스트
            TextView dayName = new TextView(this);
            dayName.setText(AttendanceReward.getDayName(date));
            dayName.setTextSize(12);
            dayName.setTextColor(getColor(R.color.text_secondary));
            dayName.setGravity(Gravity.CENTER);
            dayLayout.addView(dayName);

            // 날짜 원
            TextView dayCircle = new TextView(this);
            dayCircle.setText(AttendanceReward.getDayNumber(date));
            dayCircle.setTextSize(14);
            dayCircle.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(
                    dpToPx(36), dpToPx(36));
            circleParams.topMargin = dpToPx(4);
            dayCircle.setLayoutParams(circleParams);

            // 배경 스타일
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);

            boolean isChecked = checkedDates.contains(date);
            boolean isToday = date.equals(today);

            if (isChecked) {
                // 출석한 날 - 초록색
                bg.setColor(getColor(R.color.primary));
                dayCircle.setTextColor(Color.WHITE);
            } else if (isToday) {
                // 오늘 (미출석) - 테두리만
                bg.setColor(Color.TRANSPARENT);
                bg.setStroke(dpToPx(2), getColor(R.color.primary));
                dayCircle.setTextColor(getColor(R.color.primary));
            } else {
                // 미출석 - 회색
                bg.setColor(getColor(R.color.surface_variant));
                dayCircle.setTextColor(getColor(R.color.text_secondary));
            }

            dayCircle.setBackground(bg);
            dayLayout.addView(dayCircle);

            // 체크 마크 (출석한 경우)
            if (isChecked) {
                TextView checkMark = new TextView(this);
                checkMark.setText("✓");
                checkMark.setTextSize(10);
                checkMark.setTextColor(getColor(R.color.primary));
                checkMark.setGravity(Gravity.CENTER);
                dayLayout.addView(checkMark);
            } else {
                // 공간 맞추기
                TextView placeholder = new TextView(this);
                placeholder.setText(" ");
                placeholder.setTextSize(10);
                dayLayout.addView(placeholder);
            }

            binding.weekCalendar.addView(dayLayout);
        }
    }

    private void updateStreakUI() {
        binding.tvStreakDays.setText(currentStreak + "일");

        // 스트릭 이모지
        if (currentStreak >= 7) {
            binding.tvStreakEmoji.setText("🔥");
        } else if (currentStreak >= 3) {
            binding.tvStreakEmoji.setText("⭐");
        } else {
            binding.tvStreakEmoji.setText("📅");
        }

        // 다음 보너스까지 남은 일수
        int nextBonusDays;
        String nextBonusType;
        if (currentStreak % 7 >= 3 || currentStreak < 3) {
            // 다음 3의 배수 또는 7의 배수까지
            int daysTo3 = 3 - (currentStreak % 3);
            int daysTo7 = 7 - (currentStreak % 7);
            if (daysTo3 == 3) daysTo3 = 0;
            if (daysTo7 == 7) daysTo7 = 0;

            if (daysTo7 > 0 && daysTo7 <= daysTo3) {
                nextBonusDays = daysTo7;
                nextBonusType = "포션 보상";
            } else if (daysTo3 > 0) {
                nextBonusDays = daysTo3;
                nextBonusType = "보너스 EXP";
            } else {
                nextBonusDays = 1;
                nextBonusType = "보상";
            }
        } else {
            nextBonusDays = 7 - (currentStreak % 7);
            nextBonusType = "포션 보상";
        }

        if (nextBonusDays > 0) {
            binding.tvNextReward.setText(nextBonusType + "까지 " + nextBonusDays + "일 남음");
        } else {
            binding.tvNextReward.setText("오늘 보너스 보상!");
        }

        // 버튼 상태
        if (isCheckedInToday) {
            binding.btnCheckIn.setEnabled(false);
            binding.btnCheckIn.setText("✓ 출석 완료");
            binding.btnCheckIn.setBackgroundTintList(getColorStateList(R.color.surface_variant));
        } else {
            binding.btnCheckIn.setEnabled(true);
            binding.btnCheckIn.setText("출석 체크하기");
            binding.btnCheckIn.setBackgroundTintList(getColorStateList(R.color.primary));
        }
    }

    private void performCheckIn() {
        binding.loadingOverlay.setVisibility(View.VISIBLE);
        binding.btnCheckIn.setEnabled(false);

        String userId = firebaseRepository.getCurrentUserId();
        if (userId == null) {
            binding.loadingOverlay.setVisibility(View.GONE);
            return;
        }

        firebaseRepository.checkAttendance(userId, new FirebaseRepository.AttendanceCallback() {
            @Override
            public void onSuccess(AttendanceReward reward) {
                isCheckedInToday = true;
                currentStreak = reward.getConsecutiveDays();
                checkedDates.add(reward.getDate());

                runOnUiThread(() -> {
                    binding.loadingOverlay.setVisibility(View.GONE);
                    showTodayReward(reward);
                    updateStreakUI();
                    buildWeekCalendar();

                    Toast.makeText(AttendanceActivity.this,
                            "출석 완료! " + reward.getRewardDescription(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    binding.loadingOverlay.setVisibility(View.GONE);
                    binding.btnCheckIn.setEnabled(true);
                    Toast.makeText(AttendanceActivity.this, "출석 실패: " + message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showTodayReward(AttendanceReward reward) {
        binding.todayRewardCard.setVisibility(View.VISIBLE);

        AttendanceReward.RewardInfo info = AttendanceReward.calculateReward(reward.getConsecutiveDays());
        binding.tvRewardEmoji.setText(info.emoji);
        binding.tvRewardDescription.setText(reward.getRewardDescription());
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
