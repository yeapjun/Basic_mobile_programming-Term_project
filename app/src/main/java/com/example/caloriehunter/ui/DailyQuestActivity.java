package com.example.caloriehunter.ui;

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
import com.example.caloriehunter.data.model.DailyQuest;
import com.example.caloriehunter.data.model.User;
import com.example.caloriehunter.data.repository.FirebaseRepository;
import com.example.caloriehunter.databinding.ActivityDailyQuestBinding;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 일일 퀘스트 화면
 */
public class DailyQuestActivity extends AppCompatActivity {

    private ActivityDailyQuestBinding binding;
    private FirebaseRepository firebaseRepository;
    private QuestAdapter questAdapter;
    private List<DailyQuest> quests = new ArrayList<>();
    private User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDailyQuestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firebaseRepository = FirebaseRepository.getInstance();

        setupViews();
        loadData();
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());

        // 오늘 날짜 표시
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREAN);
        binding.tvDate.setText(sdf.format(new Date()));

        // RecyclerView 설정
        questAdapter = new QuestAdapter();
        binding.recyclerQuests.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerQuests.setAdapter(questAdapter);
    }

    private void loadData() {
        binding.progressLoading.setVisibility(View.VISIBLE);

        String userId = firebaseRepository.getCurrentUserId();
        if (userId == null) {
            Toast.makeText(this, "로그인이 필요합니다", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 유저 정보 로드
        firebaseRepository.getOrCreateUser(userId, "", new FirebaseRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                currentUser = user;
                loadQuests(userId);
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    binding.progressLoading.setVisibility(View.GONE);
                    Toast.makeText(DailyQuestActivity.this, "유저 로드 실패", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadQuests(String userId) {
        firebaseRepository.getTodayQuests(userId, new FirebaseRepository.QuestsCallback() {
            @Override
            public void onSuccess(List<DailyQuest> questList) {
                quests = questList;
                runOnUiThread(() -> {
                    binding.progressLoading.setVisibility(View.GONE);
                    questAdapter.setQuests(quests);
                    updateCompletedCount();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    binding.progressLoading.setVisibility(View.GONE);
                    Toast.makeText(DailyQuestActivity.this, "퀘스트 로드 실패: " + message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void updateCompletedCount() {
        int completed = 0;
        for (DailyQuest quest : quests) {
            if (quest.isCompleted()) {
                completed++;
            }
        }
        binding.tvCompletedCount.setText(completed + "/" + quests.size());
    }

    private void claimReward(DailyQuest quest, int position) {
        if (currentUser == null) return;

        String userId = firebaseRepository.getCurrentUserId();
        if (userId == null) return;

        // 보상 수령 처리
        firebaseRepository.claimQuestReward(userId, quest, new FirebaseRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                // 경험치 추가
                currentUser.addExp(quest.getRewardExp());
                firebaseRepository.updateUser(currentUser, new FirebaseRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            quest.setRewardClaimed(true);
                            questAdapter.notifyItemChanged(position);
                            Toast.makeText(DailyQuestActivity.this,
                                    "+" + quest.getRewardExp() + " EXP 획득!", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() ->
                                Toast.makeText(DailyQuestActivity.this, "보상 수령 실패", Toast.LENGTH_SHORT).show()
                        );
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() ->
                        Toast.makeText(DailyQuestActivity.this, "보상 수령 실패", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    /**
     * 퀘스트 어댑터
     */
    private class QuestAdapter extends RecyclerView.Adapter<QuestAdapter.QuestViewHolder> {

        private List<DailyQuest> questList = new ArrayList<>();

        void setQuests(List<DailyQuest> quests) {
            this.questList = quests;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public QuestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_quest, parent, false);
            return new QuestViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull QuestViewHolder holder, int position) {
            DailyQuest quest = questList.get(position);
            holder.bind(quest, position);
        }

        @Override
        public int getItemCount() {
            return questList.size();
        }

        class QuestViewHolder extends RecyclerView.ViewHolder {
            private final TextView tvQuestEmoji;
            private final TextView tvQuestTitle;
            private final TextView tvQuestDescription;
            private final ProgressBar progressQuest;
            private final TextView tvQuestProgress;
            private final TextView tvQuestReward;
            private final MaterialButton btnClaimReward;
            private final View completedBadge;
            private final TextView tvCompleted;

            QuestViewHolder(View itemView) {
                super(itemView);
                tvQuestEmoji = itemView.findViewById(R.id.tvQuestEmoji);
                tvQuestTitle = itemView.findViewById(R.id.tvQuestTitle);
                tvQuestDescription = itemView.findViewById(R.id.tvQuestDescription);
                progressQuest = itemView.findViewById(R.id.progressQuest);
                tvQuestProgress = itemView.findViewById(R.id.tvQuestProgress);
                tvQuestReward = itemView.findViewById(R.id.tvQuestReward);
                btnClaimReward = itemView.findViewById(R.id.btnClaimReward);
                completedBadge = itemView.findViewById(R.id.completedBadge);
                tvCompleted = itemView.findViewById(R.id.tvCompleted);
            }

            void bind(DailyQuest quest, int position) {
                tvQuestEmoji.setText(quest.getEmoji());
                tvQuestTitle.setText(quest.getTitle());
                tvQuestDescription.setText(quest.getDescription());
                tvQuestProgress.setText(quest.getCurrentProgress() + "/" + quest.getTargetCount());
                progressQuest.setProgress(quest.getProgressPercent());
                tvQuestReward.setText("+" + quest.getRewardExp() + " EXP");

                if (quest.isRewardClaimed()) {
                    // 이미 보상 수령함
                    btnClaimReward.setVisibility(View.GONE);
                    completedBadge.setVisibility(View.VISIBLE);
                    tvCompleted.setText("수령완료");
                    itemView.setAlpha(0.6f);
                } else if (quest.isCompleted()) {
                    // 완료했지만 보상 미수령
                    btnClaimReward.setVisibility(View.VISIBLE);
                    completedBadge.setVisibility(View.GONE);
                    itemView.setAlpha(1.0f);
                    btnClaimReward.setOnClickListener(v -> claimReward(quest, position));
                } else {
                    // 진행 중
                    btnClaimReward.setVisibility(View.GONE);
                    completedBadge.setVisibility(View.GONE);
                    itemView.setAlpha(1.0f);
                }
            }
        }
    }
}
