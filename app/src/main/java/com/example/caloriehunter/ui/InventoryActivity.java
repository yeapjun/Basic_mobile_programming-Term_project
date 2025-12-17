package com.example.caloriehunter.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.caloriehunter.R;
import com.example.caloriehunter.data.model.Item;
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

    private List<Item> weapons = new ArrayList<>();
    private List<Item> potions = new ArrayList<>();
    private ItemAdapter adapter;

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
        adapter = new ItemAdapter();
        binding.recyclerItems.setAdapter(adapter);
    }

    private void loadInventory() {
        String userId = firebaseRepository.getCurrentUserId();
        if (userId == null) {
            Toast.makeText(this, "로그인이 필요합니다", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

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
    }

    private void updateList() {
        List<Item> currentList;

        switch (currentTab) {
            case 0:
                currentList = weapons;
                binding.tvEmptyEmoji.setText("⚔️");
                binding.tvEmptyMessage.setText("무기가 없습니다");
                break;
            case 1:
                currentList = potions;
                binding.tvEmptyEmoji.setText("💚");
                binding.tvEmptyMessage.setText("포션이 없습니다");
                break;
            default:
                currentList = new ArrayList<>();
                binding.tvEmptyEmoji.setText("📊");
                binding.tvEmptyMessage.setText("기록이 없습니다");
                break;
        }

        adapter.setItems(currentList);

        // 빈 상태 표시
        if (currentList.isEmpty()) {
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

                // 이름
                tvItemName.setText(item.getName());

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
            }
        }
    }
}
