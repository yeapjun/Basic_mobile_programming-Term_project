package com.example.caloriehunter.ui;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.caloriehunter.R;
import com.example.caloriehunter.data.model.NutritionRecord;
import com.example.caloriehunter.data.model.NutritionStats;
import com.example.caloriehunter.data.repository.FirebaseRepository;
import com.example.caloriehunter.databinding.ActivityNutritionStatsBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 영양 통계 화면
 */
public class NutritionStatsActivity extends AppCompatActivity {

    private ActivityNutritionStatsBinding binding;
    private FirebaseRepository firebaseRepository;
    private FoodRecordAdapter foodAdapter;
    private List<NutritionRecord> todayRecords = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNutritionStatsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firebaseRepository = FirebaseRepository.getInstance();

        setupViews();
        loadData();
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());

        // RecyclerView 설정
        foodAdapter = new FoodRecordAdapter();
        binding.recyclerFoods.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerFoods.setAdapter(foodAdapter);
    }

    private void loadData() {
        binding.progressLoading.setVisibility(View.VISIBLE);

        String userId = firebaseRepository.getCurrentUserId();
        if (userId == null) {
            Toast.makeText(this, "로그인이 필요합니다", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 오늘의 영양 기록 로드
        firebaseRepository.getTodayNutritionRecords(userId, new FirebaseRepository.NutritionRecordsCallback() {
            @Override
            public void onSuccess(List<NutritionRecord> records) {
                todayRecords = records;
                runOnUiThread(() -> {
                    binding.progressLoading.setVisibility(View.GONE);
                    updateUI();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    binding.progressLoading.setVisibility(View.GONE);
                    updateUI();  // 빈 상태로 표시
                });
            }
        });
    }

    private void updateUI() {
        if (todayRecords.isEmpty()) {
            binding.emptyState.setVisibility(View.VISIBLE);
            // 기본값 표시
            binding.tvHealthGrade.setText("-");
            binding.tvHealthScore.setText("기록 없음");
            binding.tvHealthyCount.setText("0");
            binding.tvUnhealthyCount.setText("0");
            updateNutrientBars(new NutritionStats());
            return;
        }

        binding.emptyState.setVisibility(View.GONE);

        // 통계 계산
        NutritionStats stats = NutritionStats.fromRecords(todayRecords);

        // 건강 점수 표시
        String grade = stats.getHealthGrade();
        int score = stats.getHealthScore();

        binding.tvHealthGrade.setText(grade);
        binding.tvHealthGrade.setTextColor(Color.parseColor(stats.getHealthGradeColor()));
        binding.tvHealthScore.setText(score + "점");

        // 건강식/불건강식 수
        binding.tvHealthyCount.setText(String.valueOf(stats.getHealthyCount()));
        binding.tvUnhealthyCount.setText(String.valueOf(stats.getUnhealthyCount()));

        // 영양소 바 업데이트
        updateNutrientBars(stats);

        // 음식 목록 업데이트
        foodAdapter.setRecords(todayRecords);
    }

    private void updateNutrientBars(NutritionStats stats) {
        // 칼로리
        updateNutrientBar(
                binding.nutrientCalories.tvNutrientName,
                binding.nutrientCalories.tvNutrientValue,
                binding.nutrientCalories.progressNutrient,
                binding.nutrientCalories.tvNutrientPercent,
                "칼로리",
                stats.getTotalCalories(),
                NutritionStats.DAILY_CALORIES,
                "kcal"
        );

        // 단백질
        updateNutrientBar(
                binding.nutrientProtein.tvNutrientName,
                binding.nutrientProtein.tvNutrientValue,
                binding.nutrientProtein.progressNutrient,
                binding.nutrientProtein.tvNutrientPercent,
                "단백질",
                stats.getTotalProtein(),
                NutritionStats.DAILY_PROTEIN,
                "g"
        );

        // 당류
        updateNutrientBar(
                binding.nutrientSugar.tvNutrientName,
                binding.nutrientSugar.tvNutrientValue,
                binding.nutrientSugar.progressNutrient,
                binding.nutrientSugar.tvNutrientPercent,
                "당류",
                stats.getTotalSugar(),
                NutritionStats.DAILY_SUGAR,
                "g"
        );

        // 나트륨
        updateNutrientBar(
                binding.nutrientSodium.tvNutrientName,
                binding.nutrientSodium.tvNutrientValue,
                binding.nutrientSodium.progressNutrient,
                binding.nutrientSodium.tvNutrientPercent,
                "나트륨",
                stats.getTotalSodium(),
                NutritionStats.DAILY_SODIUM,
                "mg"
        );

        // 식이섬유
        updateNutrientBar(
                binding.nutrientFiber.tvNutrientName,
                binding.nutrientFiber.tvNutrientValue,
                binding.nutrientFiber.progressNutrient,
                binding.nutrientFiber.tvNutrientPercent,
                "식이섬유",
                stats.getTotalFiber(),
                NutritionStats.DAILY_FIBER,
                "g"
        );
    }

    private void updateNutrientBar(TextView tvName, TextView tvValue, ProgressBar progress,
                                   TextView tvPercent, String name, float value, float daily, String unit) {
        tvName.setText(name);
        tvValue.setText(String.format(Locale.getDefault(), "%.0f / %.0f %s", value, daily, unit));

        int percent = (int) (value / daily * 100);
        progress.setProgress(Math.min(percent, 100));
        tvPercent.setText(percent + "%");

        // 초과 시 색상 변경
        if (percent > 100) {
            tvPercent.setTextColor(getColor(R.color.hp_red));
        } else if (percent > 80) {
            tvPercent.setTextColor(getColor(R.color.exp_yellow));
        } else {
            tvPercent.setTextColor(getColor(R.color.text_tertiary));
        }
    }

    /**
     * 음식 기록 어댑터
     */
    private class FoodRecordAdapter extends RecyclerView.Adapter<FoodRecordAdapter.FoodViewHolder> {

        private List<NutritionRecord> records = new ArrayList<>();

        void setRecords(List<NutritionRecord> records) {
            this.records = records;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public FoodViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_food_record, parent, false);
            return new FoodViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FoodViewHolder holder, int position) {
            NutritionRecord record = records.get(position);
            holder.bind(record);
        }

        @Override
        public int getItemCount() {
            return records.size();
        }

        class FoodViewHolder extends RecyclerView.ViewHolder {
            private final TextView tvFoodIcon;
            private final TextView tvFoodName;
            private final TextView tvFoodType;
            private final TextView tvFoodCalories;
            private final TextView tvFoodTime;

            FoodViewHolder(View itemView) {
                super(itemView);
                tvFoodIcon = itemView.findViewById(R.id.tvFoodIcon);
                tvFoodName = itemView.findViewById(R.id.tvFoodName);
                tvFoodType = itemView.findViewById(R.id.tvFoodType);
                tvFoodCalories = itemView.findViewById(R.id.tvFoodCalories);
                tvFoodTime = itemView.findViewById(R.id.tvFoodTime);
            }

            void bind(NutritionRecord record) {
                tvFoodName.setText(record.getFoodName());
                tvFoodCalories.setText(String.format(Locale.getDefault(), "%.0f kcal", record.getCalories()));

                // 시간 표시
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                tvFoodTime.setText(sdf.format(new Date(record.getTimestamp())));

                // 아이콘 및 타입 표시
                if (record.getIsHealthy()) {
                    tvFoodIcon.setText("🥗");
                    tvFoodType.setText("아이템");
                    tvFoodType.setBackgroundResource(R.drawable.bg_food_type_healthy);
                } else {
                    tvFoodIcon.setText("🍔");
                    tvFoodType.setText("몬스터");
                    tvFoodType.setBackgroundResource(R.drawable.bg_food_type_badge);
                }
            }
        }
    }
}
