package com.example.caloriehunter.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.caloriehunter.R;
import com.example.caloriehunter.data.model.BattleLog;
import com.example.caloriehunter.data.model.Item;
import com.example.caloriehunter.data.model.User;
import com.example.caloriehunter.data.repository.FirebaseRepository;
import com.example.caloriehunter.databinding.ActivityInventoryBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 인벤토리 화면
 * - 무기/포션 목록 표시
 * - 탭으로 분류
 */
public class InventoryActivity extends AppCompatActivity {

    private ActivityInventoryBinding binding;
    private FirebaseRepository firebaseRepository;

    private User currentUser;
    private List<Item> weapons = new ArrayList<>();
    private List<Item> potions = new ArrayList<>();
    private List<BattleLog> battleLogs = new ArrayList<>();
    private ItemAdapter itemAdapter;
    private BattleLogAdapter logAdapter;

    private int currentTab = 0; // 0: 무기, 1: 포션, 2: 기록

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInventoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firebaseRepository = FirebaseRepository.getInstance();

        setupViews();
        loadInventory();
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());

        // 탭 설정
        binding.tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                updateList();
            }

            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });

        // RecyclerView 설정
        itemAdapter = new ItemAdapter();
        logAdapter = new BattleLogAdapter();
        binding.recyclerItems.setAdapter(itemAdapter);
    }

    private void loadInventory() {
        String userId = firebaseRepository.getCurrentUserId();
        if (userId == null) {
            Toast.makeText(this, "로그인이 필요합니다", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 유저 정보 로드 (장착 정보 확인용)
        firebaseRepository.getOrCreateUser(userId, "", new FirebaseRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                currentUser = user;
                runOnUiThread(() -> updateEquippedInfo());
            }

            @Override
            public void onError(String message) {}
        });

        // 무기 로드
        firebaseRepository.getWeapons(userId, new FirebaseRepository.ItemsCallback() {
            @Override
            public void onSuccess(List<Item> items) {
                weapons = items;
                runOnUiThread(() -> {
                    if (currentTab == 0) updateList();
                });
            }

            @Override
            public void onError(String message) {
                weapons = new ArrayList<>();
            }
        });

        // 포션 로드
        firebaseRepository.getPotions(userId, new FirebaseRepository.ItemsCallback() {
            @Override
            public void onSuccess(List<Item> items) {
                potions = items;
                runOnUiThread(() -> {
                    if (currentTab == 1) updateList();
                });
            }

            @Override
            public void onError(String message) {
                potions = new ArrayList<>();
            }
        });

        // 전투 기록 로드
        firebaseRepository.getBattleLogs(userId, new FirebaseRepository.BattleLogsCallback() {
            @Override
            public void onSuccess(List<BattleLog> logs) {
                battleLogs = logs;
                runOnUiThread(() -> {
                    if (currentTab == 2) updateList();
                });
            }

            @Override
            public void onError(String message) {
                battleLogs = new ArrayList<>();
            }
        });
    }

    private void updateEquippedInfo() {
        if (currentUser != null && currentUser.getEquippedWeaponName() != null) {
            binding.tvEquippedWeapon.setText("⚔️ " + currentUser.getEquippedWeaponName() + " (ATK +" + currentUser.getEquippedWeaponPower() + ")");
            binding.tvEquippedWeapon.setVisibility(View.VISIBLE);
        } else {
            binding.tvEquippedWeapon.setText("장착된 무기 없음");
            binding.tvEquippedWeapon.setVisibility(View.VISIBLE);
        }
    }

    private void showEquipDialog(Item item) {
        if (currentUser == null) return;

        boolean isEquipped = item.getId().equals(currentUser.getEquippedWeaponId());
        String title = isEquipped ? "무기 해제" : "무기 장착";
        String message = isEquipped
            ? "'" + item.getName() + "'을(를) 해제하시겠습니까?"
            : "'" + item.getName() + "'을(를) 장착하시겠습니까?\nATK +" + item.getAttackPower();

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(isEquipped ? "해제" : "장착", (dialog, which) -> {
                    if (isEquipped) {
                        currentUser.unequipWeapon();
                    } else {
                        currentUser.equipWeapon(item);
                    }
                    saveUserAndUpdate();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void saveUserAndUpdate() {
        firebaseRepository.updateUser(currentUser, new FirebaseRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    updateEquippedInfo();
                    itemAdapter.notifyDataSetChanged();
                    String msg = currentUser.getEquippedWeaponName() != null
                        ? "⚔️ " + currentUser.getEquippedWeaponName() + " 장착!"
                        : "무기 해제됨";
                    Toast.makeText(InventoryActivity.this, msg, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() ->
                    Toast.makeText(InventoryActivity.this, "저장 실패", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void updateList() {
        boolean isEmpty;

        switch (currentTab) {
            case 0:
                binding.recyclerItems.setLayoutManager(new GridLayoutManager(this, 2));
                binding.recyclerItems.setAdapter(itemAdapter);
                itemAdapter.setItems(weapons);
                binding.tvEmptyEmoji.setText("⚔️");
                binding.tvEmptyMessage.setText("무기가 없습니다");
                isEmpty = weapons.isEmpty();
                break;
            case 1:
                binding.recyclerItems.setLayoutManager(new GridLayoutManager(this, 2));
                binding.recyclerItems.setAdapter(itemAdapter);
                itemAdapter.setItems(potions);
                binding.tvEmptyEmoji.setText("💚");
                binding.tvEmptyMessage.setText("포션이 없습니다");
                isEmpty = potions.isEmpty();
                break;
            case 2:
                binding.recyclerItems.setLayoutManager(new LinearLayoutManager(this));
                binding.recyclerItems.setAdapter(logAdapter);
                logAdapter.setLogs(battleLogs);
                binding.tvEmptyEmoji.setText("📊");
                binding.tvEmptyMessage.setText("전투 기록이 없습니다\n몬스터를 처치하면 기록됩니다");
                isEmpty = battleLogs.isEmpty();
                break;
            default:
                isEmpty = true;
                break;
        }

        // 빈 상태 표시
        if (isEmpty) {
            binding.emptyState.setVisibility(View.VISIBLE);
            binding.recyclerItems.setVisibility(View.GONE);
        } else {
            binding.emptyState.setVisibility(View.GONE);
            binding.recyclerItems.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 아이템 어댑터
     */
    private class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ItemViewHolder> {

        private List<Item> items = new ArrayList<>();

        void setItems(List<Item> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_inventory, parent, false);
            return new ItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
            Item item = items.get(position);
            holder.bind(item);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ItemViewHolder extends RecyclerView.ViewHolder {
            private final TextView tvRarity;
            private final TextView tvItemEmoji;
            private final TextView tvItemName;
            private final TextView tvItemStat;
            private final TextView tvItemQuantity;

            ItemViewHolder(View itemView) {
                super(itemView);
                tvRarity = itemView.findViewById(R.id.tvRarity);
                tvItemEmoji = itemView.findViewById(R.id.tvItemEmoji);
                tvItemName = itemView.findViewById(R.id.tvItemName);
                tvItemStat = itemView.findViewById(R.id.tvItemStat);
                tvItemQuantity = itemView.findViewById(R.id.tvItemQuantity);
            }

            void bind(Item item) {
                // 등급
                tvRarity.setText(item.getRarity().toUpperCase());
                tvRarity.setTextColor(android.graphics.Color.parseColor(item.getRarityColor()));

                // 이모지
                tvItemEmoji.setText(item.getTypeEmoji());

                // 이름 (장착 중이면 표시)
                boolean isEquipped = currentUser != null &&
                        item.getId() != null &&
                        item.getId().equals(currentUser.getEquippedWeaponId());

                if (isEquipped) {
                    tvItemName.setText(item.getName() + " [장착중]");
                    itemView.setBackgroundColor(getColor(R.color.primary_dark));
                } else {
                    tvItemName.setText(item.getName());
                    itemView.setBackgroundColor(getColor(R.color.surface_dark));
                }

                // 스탯
                String stat;
                if (item.getType() == Item.ItemType.WEAPON) {
                    stat = "ATK +" + item.getAttackPower();
                    tvItemStat.setTextColor(getColor(R.color.primary));
                } else if (item.getType() == Item.ItemType.POTION) {
                    stat = "HP +" + item.getHealAmount();
                    tvItemStat.setTextColor(getColor(R.color.hp_green));
                } else {
                    stat = "BUFF +" + item.getBuffPower();
                    tvItemStat.setTextColor(getColor(R.color.exp_yellow));
                }
                tvItemStat.setText(stat);

                // 수량
                tvItemQuantity.setText("x" + item.getQuantity());

                // 무기인 경우 클릭 시 장착/해제
                if (item.getType() == Item.ItemType.WEAPON) {
                    itemView.setOnClickListener(v -> showEquipDialog(item));
                } else {
                    itemView.setOnClickListener(null);
                }
            }
        }
    }

    /**
     * 전투 기록 어댑터
     */
    private class BattleLogAdapter extends RecyclerView.Adapter<BattleLogAdapter.LogViewHolder> {

        private List<BattleLog> logs = new ArrayList<>();

        void setLogs(List<BattleLog> logs) {
            this.logs = logs;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_battle_log, parent, false);
            return new LogViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
            BattleLog log = logs.get(position);
            holder.bind(log);
        }

        @Override
        public int getItemCount() {
            return logs.size();
        }

        class LogViewHolder extends RecyclerView.ViewHolder {
            private final TextView tvLogEmoji;
            private final TextView tvLogTier;
            private final TextView tvLogMonsterName;
            private final TextView tvLogFoodName;
            private final TextView tvLogExp;
            private final TextView tvLogTime;

            LogViewHolder(View itemView) {
                super(itemView);
                tvLogEmoji = itemView.findViewById(R.id.tvLogEmoji);
                tvLogTier = itemView.findViewById(R.id.tvLogTier);
                tvLogMonsterName = itemView.findViewById(R.id.tvLogMonsterName);
                tvLogFoodName = itemView.findViewById(R.id.tvLogFoodName);
                tvLogExp = itemView.findViewById(R.id.tvLogExp);
                tvLogTime = itemView.findViewById(R.id.tvLogTime);
            }

            void bind(BattleLog log) {
                tvLogEmoji.setText(log.getElementEmoji());
                tvLogTier.setText(log.getMonsterTier().toUpperCase());
                tvLogTier.setTextColor(android.graphics.Color.parseColor(log.getTierColor()));
                tvLogMonsterName.setText(log.getMonsterName());
                tvLogFoodName.setText(log.getFoodName());
                tvLogExp.setText("+" + log.getExpGained() + " EXP");
                tvLogTime.setText(log.getTimeAgo());
            }
        }
    }
}
