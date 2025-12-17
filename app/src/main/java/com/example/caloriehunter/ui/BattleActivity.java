package com.example.caloriehunter.ui;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.caloriehunter.R;
import com.example.caloriehunter.data.model.Item;
import com.example.caloriehunter.data.model.Monster;
import com.example.caloriehunter.data.model.User;
import com.example.caloriehunter.data.repository.FirebaseRepository;
import com.example.caloriehunter.databinding.ActivityBattleBinding;

import java.util.List;
import java.util.Random;

/**
 * 전투 화면
 * - 턴제 전투 시스템
 * - 공격/포션 사용
 * - 승리/패배 처리
 */
public class BattleActivity extends AppCompatActivity {

    private ActivityBattleBinding binding;
    private FirebaseRepository firebaseRepository;

    private Monster monster;
    private User user;
    private List<Item> potions;

    private int userCurrentHp;
    private int monsterCurrentHp;
    private boolean isBattleOver = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final StringBuilder battleLog = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBattleBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firebaseRepository = FirebaseRepository.getInstance();

        String monsterId = getIntent().getStringExtra("monster_id");
        if (monsterId == null) {
            finish();
            return;
        }

        setupClickListeners();
        loadBattleData(monsterId);
    }

    private void setupClickListeners() {
        binding.btnAttack.setOnClickListener(v -> {
            if (!isBattleOver) {
                playerAttack();
            }
        });

        binding.btnPotion.setOnClickListener(v -> {
            if (!isBattleOver) {
                usePotion();
            }
        });

        binding.btnRun.setOnClickListener(v -> {
            showRunConfirmDialog();
        });

        binding.btnResultConfirm.setOnClickListener(v -> {
            finish();
        });
    }

    private void loadBattleData(String monsterId) {
        String userId = firebaseRepository.getCurrentUserId();
        if (userId == null) {
            finish();
            return;
        }

        // 몬스터 로드
        firebaseRepository.getActiveMonster(userId, new FirebaseRepository.MonsterCallback() {
            @Override
            public void onSuccess(Monster m) {
                monster = m;
                monsterCurrentHp = monster.getHp();
                runOnUiThread(() -> updateMonsterUI());

                // 유저 로드
                loadUserData(userId);
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(BattleActivity.this, "몬스터 로드 실패", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void loadUserData(String userId) {
        firebaseRepository.getOrCreateUser(userId, "", new FirebaseRepository.UserCallback() {
            @Override
            public void onSuccess(User u) {
                user = u;
                userCurrentHp = user.getHp();
                runOnUiThread(() -> updatePlayerUI());

                // 포션 로드
                loadPotions(userId);
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(BattleActivity.this, "유저 로드 실패", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });

        // 포션 로드
        firebaseRepository.getPotions(userId, new FirebaseRepository.ItemsCallback() {
            @Override
            public void onSuccess(List<Item> items) {
                potions = items;
                runOnUiThread(() -> updatePotionButton());
            }

            @Override
            public void onError(String message) {
                potions = null;
            }
        });
    }

    private void loadPotions(String userId) {
        firebaseRepository.getPotions(userId, new FirebaseRepository.ItemsCallback() {
            @Override
            public void onSuccess(List<Item> items) {
                potions = items;
                runOnUiThread(() -> updatePotionButton());
            }

            @Override
            public void onError(String message) {
                // 포션 없음
            }
        });
    }

    private void updateMonsterUI() {
        if (monster == null) return;

        binding.tvMonsterTier.setText(monster.getTier().toUpperCase());
        binding.tvMonsterTier.setTextColor(android.graphics.Color.parseColor(monster.getTierColor()));
        binding.tvMonsterName.setText(monster.getName());
        binding.tvMonsterEmoji.setText(monster.getElementEmoji());
        binding.tvMonsterHp.setText(monsterCurrentHp + " / " + monster.getMaxHp());

        int hpPercent = (int) ((float) monsterCurrentHp / monster.getMaxHp() * 100);
        binding.progressMonsterHp.setProgress(hpPercent);
    }

    private void updatePlayerUI() {
        if (user == null) return;

        binding.tvPlayerLevel.setText(String.valueOf(user.getLevel()));
        binding.tvPlayerName.setText(user.getNickname());
        binding.tvPlayerHp.setText(userCurrentHp + "/" + user.getMaxHp());

        int hpPercent = (int) ((float) userCurrentHp / user.getMaxHp() * 100);
        binding.progressPlayerHp.setProgress(hpPercent);
    }

    private void updatePotionButton() {
        int potionCount = potions != null ? potions.size() : 0;
        binding.btnPotion.setText("💚 포션 (" + potionCount + ")");
        binding.btnPotion.setEnabled(potionCount > 0);
    }

    private void playerAttack() {
        setActionsEnabled(false);

        // 기본 공격력 + 무기 보너스 (MVP에서는 기본값 사용)
        int baseDamage = 10 + user.getLevel() * 2;
        int finalDamage = Math.max(1, baseDamage - monster.getDefense() / 2);

        // 크리티컬 확률 (10%)
        boolean isCritical = random.nextInt(100) < 10;
        if (isCritical) {
            finalDamage *= 2;
        }

        // 데미지 적용
        monsterCurrentHp = Math.max(0, monsterCurrentHp - finalDamage);

        // 로그
        String attackLog = isCritical
                ? "💥 크리티컬! " + finalDamage + " 데미지!"
                : "⚔️ " + finalDamage + " 데미지를 입혔다!";
        addBattleLog(attackLog);

        // 데미지 팝업 애니메이션
        showDamagePopup("-" + finalDamage, isCritical);

        // UI 업데이트
        updateMonsterUI();

        // 승리 체크
        if (monsterCurrentHp <= 0) {
            handler.postDelayed(this::handleVictory, 500);
        } else {
            // 몬스터 반격
            handler.postDelayed(this::monsterAttack, 800);
        }
    }

    private void monsterAttack() {
        int damage = monster.getAttack();

        // 독 데미지 추가
        if (monster.getPoisonDamage() > 0) {
            damage += monster.getPoisonDamage();
            addBattleLog("☠️ 독 효과! +" + monster.getPoisonDamage() + " 추가 피해!");
        }

        userCurrentHp = Math.max(0, userCurrentHp - damage);
        addBattleLog("🔴 " + monster.getName() + "의 공격! " + damage + " 데미지!");

        updatePlayerUI();

        // 패배 체크
        if (userCurrentHp <= 0) {
            handler.postDelayed(this::handleDefeat, 500);
        } else {
            setActionsEnabled(true);
        }
    }

    private void usePotion() {
        if (potions == null || potions.isEmpty()) {
            Toast.makeText(this, "포션이 없습니다", Toast.LENGTH_SHORT).show();
            return;
        }

        setActionsEnabled(false);

        Item potion = potions.get(0);
        int healAmount = potion.getHealAmount();

        // HP 회복
        int oldHp = userCurrentHp;
        userCurrentHp = Math.min(user.getMaxHp(), userCurrentHp + healAmount);
        int actualHeal = userCurrentHp - oldHp;

        addBattleLog("💚 " + potion.getName() + " 사용! HP +" + actualHeal);

        // 포션 소비
        String userId = firebaseRepository.getCurrentUserId();
        firebaseRepository.useItem(userId, potion, new FirebaseRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                potions.remove(0);
                runOnUiThread(() -> {
                    updatePotionButton();
                    updatePlayerUI();
                });
            }

            @Override
            public void onError(String message) {
                // 실패해도 진행
            }
        });

        updatePlayerUI();
        updatePotionButton();

        // 몬스터 반격
        handler.postDelayed(this::monsterAttack, 800);
    }

    private void handleVictory() {
        isBattleOver = true;

        // 경험치 계산 (몬스터 레벨 기반)
        int expGain = 20 + monster.getMaxHp() / 2;

        addBattleLog("🎉 " + monster.getName() + "을(를) 처치했다!");
        addBattleLog("+" + expGain + " EXP 획득!");

        // 몬스터 처치 처리
        String userId = firebaseRepository.getCurrentUserId();
        firebaseRepository.defeatMonster(monster.getId(), userId, new FirebaseRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                // 경험치 추가
                user.addExp(expGain);
                user.setTotalDamageDealt(user.getTotalDamageDealt() + monster.getMaxHp());
                firebaseRepository.updateUser(user, new FirebaseRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {}
                    @Override
                    public void onError(String message) {}
                });
            }
            @Override
            public void onError(String message) {}
        });

        // 결과 화면
        showResultOverlay(true, expGain);
    }

    private void handleDefeat() {
        isBattleOver = true;
        addBattleLog("💀 패배...");

        showResultOverlay(false, 0);
    }

    private void showResultOverlay(boolean isVictory, int expGain) {
        binding.resultOverlay.setVisibility(View.VISIBLE);

        if (isVictory) {
            binding.tvResultEmoji.setText("🎉");
            binding.tvResultTitle.setText("승리!");
            binding.tvResultMessage.setText("+" + expGain + " EXP 획득!");
            binding.tvResultMessage.setTextColor(getColor(R.color.exp_yellow));
        } else {
            binding.tvResultEmoji.setText("💀");
            binding.tvResultTitle.setText("패배...");
            binding.tvResultMessage.setText("다음에 다시 도전하세요");
            binding.tvResultMessage.setTextColor(getColor(R.color.text_secondary));
        }
    }

    private void showDamagePopup(String text, boolean isCritical) {
        binding.tvDamagePopup.setText(text);
        binding.tvDamagePopup.setTextColor(isCritical
                ? getColor(R.color.exp_yellow)
                : getColor(R.color.hp_red));
        binding.tvDamagePopup.setVisibility(View.VISIBLE);

        // 위로 떠오르는 애니메이션
        ObjectAnimator animator = ObjectAnimator.ofFloat(binding.tvDamagePopup, "translationY", 0f, -100f);
        animator.setDuration(500);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.start();

        handler.postDelayed(() -> {
            binding.tvDamagePopup.setVisibility(View.INVISIBLE);
            binding.tvDamagePopup.setTranslationY(0);
        }, 500);
    }

    private void addBattleLog(String message) {
        battleLog.append(message).append("\n");
        binding.tvBattleLog.setText(battleLog.toString());
    }

    private void setActionsEnabled(boolean enabled) {
        binding.btnAttack.setEnabled(enabled);
        binding.btnPotion.setEnabled(enabled && potions != null && !potions.isEmpty());
        binding.btnRun.setEnabled(enabled);
    }

    private void showRunConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("도망가기")
                .setMessage("전투에서 도망가시겠습니까?\n몬스터는 그대로 남아있습니다.")
                .setPositiveButton("도망", (dialog, which) -> finish())
                .setNegativeButton("취소", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (isBattleOver) {
            super.onBackPressed();
        } else {
            showRunConfirmDialog();
        }
    }
}
