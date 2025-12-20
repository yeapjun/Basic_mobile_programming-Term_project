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
import com.example.caloriehunter.data.model.BattleLog;
import com.example.caloriehunter.data.model.DailyQuest;
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
    private List<Item> buffs;  // 버프 아이템 목록
    private Item equippedWeapon;  // 장착된 무기

    private int userCurrentHp;
    private int monsterCurrentHp;
    private boolean isBattleOver = false;

    // 버프 상태 관리 (1회성)
    private boolean isAttackBuffActive = false;
    private boolean isDefenseBuffActive = false;
    private float attackBuffMultiplier = 1.0f;
    private float defenseBuffMultiplier = 1.0f;

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
                showItemSelectionDialog("POTION", potions);
            }
        });

        binding.btnBuff.setOnClickListener(v -> {
            if (!isBattleOver) {
                if (isAttackBuffActive || isDefenseBuffActive) {
                    Toast.makeText(this, "이미 버프가 적용 중입니다!", Toast.LENGTH_SHORT).show();
                } else {
                    showItemSelectionDialog("BUFF", buffs);
                }
            }
        });

        binding.btnRun.setOnClickListener(v -> {
            showRunConfirmDialog();
        });

        binding.btnResultConfirm.setOnClickListener(v -> {
            // 메인화면으로 돌아가기 (결과 전달)
            setResult(RESULT_OK);
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

                // 버프 로드
                loadBuffs(userId);

                // 장착 무기 로드
                loadEquippedWeapon(userId);
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(BattleActivity.this, "유저 로드 실패", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void loadEquippedWeapon(String userId) {
        if (user == null || user.getEquippedWeaponId() == null || user.getEquippedWeaponId().isEmpty()) {
            equippedWeapon = null;
            return;
        }

        firebaseRepository.getWeapon(userId, user.getEquippedWeaponId(), new FirebaseRepository.ItemCallback() {
            @Override
            public void onSuccess(Item item) {
                equippedWeapon = item;
            }

            @Override
            public void onError(String message) {
                equippedWeapon = null;
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

    private void loadBuffs(String userId) {
        firebaseRepository.getBuffs(userId, new FirebaseRepository.ItemsCallback() {
            @Override
            public void onSuccess(List<Item> items) {
                buffs = items;
                runOnUiThread(() -> updateBuffButton());
            }

            @Override
            public void onError(String message) {
                // 버프 없음
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

    private void updateBuffButton() {
        int buffCount = buffs != null ? buffs.size() : 0;
        if (isAttackBuffActive) {
            binding.btnBuff.setText("⚔️ 공격강화!");
            binding.btnBuff.setEnabled(false);
        } else if (isDefenseBuffActive) {
            binding.btnBuff.setText("🛡️ 수비강화!");
            binding.btnBuff.setEnabled(false);
        } else {
            binding.btnBuff.setText("✨ 버프 (" + buffCount + ")");
            binding.btnBuff.setEnabled(buffCount > 0);
        }
    }

    private void playerAttack() {
        setActionsEnabled(false);

        // 기본 공격력 + 장착 무기 보너스
        int baseDamage = user.getTotalAttackPower();

        // 공격 버프 적용 (1회성)
        if (isAttackBuffActive) {
            baseDamage = (int) (baseDamage * attackBuffMultiplier);
            addBattleLog("💪 공격 버프 발동! (x" + attackBuffMultiplier + ")");
            isAttackBuffActive = false;
            attackBuffMultiplier = 1.0f;
            updateBuffButton();
        }

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

        // 무기 내구도 감소
        reduceWeaponDurability();

        // 승리 체크
        if (monsterCurrentHp <= 0) {
            handler.postDelayed(this::handleVictory, 500);
        } else {
            // 몬스터 반격
            handler.postDelayed(this::monsterAttack, 800);
        }
    }

    private void reduceWeaponDurability() {
        if (equippedWeapon == null) return;

        String userId = firebaseRepository.getCurrentUserId();
        if (userId == null) return;

        // 내구도 1 감소
        boolean isDestroyed = equippedWeapon.reduceDurability(1);

        if (isDestroyed) {
            // 무기 파괴!
            addBattleLog("💔 " + equippedWeapon.getName() + "이(가) 부서졌다!");

            // Firebase에서 무기 삭제
            firebaseRepository.deleteWeapon(userId, equippedWeapon.getId(), new FirebaseRepository.SimpleCallback() {
                @Override
                public void onSuccess() {}
                @Override
                public void onError(String message) {}
            });

            // 유저의 장착 무기 해제
            firebaseRepository.unequipWeapon(userId, new FirebaseRepository.SimpleCallback() {
                @Override
                public void onSuccess() {
                    user.setEquippedWeaponId(null);
                    user.setEquippedWeaponName(null);
                    user.setEquippedWeaponPower(0);
                }
                @Override
                public void onError(String message) {}
            });

            equippedWeapon = null;

            runOnUiThread(() -> {
                Toast.makeText(this, "무기가 부서졌습니다!", Toast.LENGTH_SHORT).show();
            });
        } else {
            // 내구도 업데이트
            addBattleLog("🔧 무기 내구도: " + equippedWeapon.getDurability() + "/" + equippedWeapon.getMaxDurability());
            firebaseRepository.updateWeaponDurability(userId, equippedWeapon.getId(),
                    equippedWeapon.getDurability(), new FirebaseRepository.SimpleCallback() {
                @Override
                public void onSuccess() {}
                @Override
                public void onError(String message) {}
            });
        }
    }

    private void monsterAttack() {
        int damage = monster.getAttack();

        // 독 데미지 추가
        if (monster.getPoisonDamage() > 0) {
            damage += monster.getPoisonDamage();
            addBattleLog("☠️ 독 효과! +" + monster.getPoisonDamage() + " 추가 피해!");
        }

        // 수비 버프 적용 (1회성)
        if (isDefenseBuffActive) {
            int originalDamage = damage;
            damage = (int) (damage * defenseBuffMultiplier);
            addBattleLog("🛡️ 수비 버프 발동! 데미지 " + originalDamage + " → " + damage);
            isDefenseBuffActive = false;
            defenseBuffMultiplier = 1.0f;
            updateBuffButton();
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

    /**
     * 아이템 선택 다이얼로그 표시
     * @param itemType "POTION" 또는 "BUFF"
     * @param items 아이템 목록
     */
    private void showItemSelectionDialog(String itemType, List<Item> items) {
        if (items == null || items.isEmpty()) {
            String message = itemType.equals("POTION") ? "포션이 없습니다" : "버프 아이템이 없습니다";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            return;
        }

        String title = itemType.equals("POTION") ? "포션 선택" : "버프 선택";
        String[] itemNames = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (itemType.equals("POTION")) {
                itemNames[i] = item.getName() + " (HP +" + item.getHealAmount() + ")";
            } else {
                // 버프 종류에 따라 다른 정보 표시
                if (item.isAttackBuff()) {
                    itemNames[i] = "⚔️ " + item.getName() + " (공격력 x" + item.getAttackBuffMultiplier() + ")";
                } else if (item.isDefenseBuff()) {
                    itemNames[i] = "🛡️ " + item.getName() + " (데미지 " + item.getDefenseBoost() + "% 감소)";
                } else {
                    itemNames[i] = item.getName();
                }
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(itemNames, (dialog, which) -> {
                    Item selectedItem = items.get(which);
                    if (itemType.equals("POTION")) {
                        useSelectedPotion(selectedItem, which);
                    } else {
                        useBuff(selectedItem, which);
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /**
     * 선택한 포션 사용
     */
    private void useSelectedPotion(Item potion, int index) {
        setActionsEnabled(false);

        int healAmount = potion.getHealAmount();

        // HP 회복
        int oldHp = userCurrentHp;
        userCurrentHp = Math.min(user.getMaxHp(), userCurrentHp + healAmount);
        int actualHeal = userCurrentHp - oldHp;

        addBattleLog("💚 " + potion.getName() + " 사용! HP +" + actualHeal);

        // 포션 리스트에서 즉시 제거 (UI 동기화)
        potions.remove(index);
        updatePlayerUI();
        updatePotionButton();

        // 포션 소비 (Firebase 동기화)
        String userId = firebaseRepository.getCurrentUserId();
        if (userId != null) {
            firebaseRepository.useItem(userId, potion, new FirebaseRepository.SimpleCallback() {
                @Override
                public void onSuccess() {
                    // 이미 로컬에서 제거됨
                }

                @Override
                public void onError(String message) {
                    // 실패해도 진행 (로컬 상태 우선)
                }
            });
        }

        // 몬스터 반격
        handler.postDelayed(this::monsterAttack, 800);
    }

    /**
     * 버프 아이템 사용 (1회성)
     */
    private void useBuff(Item buff, int index) {
        // 버프 효과 적용 (다음 1회 공격 또는 피격에 적용)
        if (buff.isAttackBuff()) {
            attackBuffMultiplier = buff.getAttackBuffMultiplier();
            isAttackBuffActive = true;
            addBattleLog("⚔️ " + buff.getName() + " 사용! 다음 공격 x" + attackBuffMultiplier);
        } else if (buff.isDefenseBuff()) {
            defenseBuffMultiplier = buff.getDefenseBuffMultiplier();
            isDefenseBuffActive = true;
            addBattleLog("🛡️ " + buff.getName() + " 사용! 다음 피격 데미지 " + buff.getDefenseBoost() + "% 감소");
        }

        // 버프 리스트에서 즉시 제거 (UI 동기화)
        buffs.remove(index);
        updateBuffButton();

        // 버프 소비 (Firebase 동기화)
        String userId = firebaseRepository.getCurrentUserId();
        if (userId != null) {
            firebaseRepository.useItem(userId, buff, new FirebaseRepository.SimpleCallback() {
                @Override
                public void onSuccess() {
                    // 이미 로컬에서 제거됨
                }

                @Override
                public void onError(String message) {
                    // 실패해도 진행 (로컬 상태 우선)
                }
            });
        }

        // 버프는 턴을 소비하지 않음 (바로 다음 행동 가능)
        setActionsEnabled(true);
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
                user.setTotalMonstersKilled(user.getTotalMonstersKilled() + 1);
                firebaseRepository.updateUser(user, new FirebaseRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {}
                    @Override
                    public void onError(String message) {}
                });

                // 전투 기록 저장
                BattleLog log = BattleLog.createVictoryLog(userId, monster, expGain);
                firebaseRepository.saveBattleLog(log, new FirebaseRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {}
                    @Override
                    public void onError(String message) {}
                });

                // 몬스터 처치 퀘스트 진행
                firebaseRepository.progressQuestByType(userId, DailyQuest.QuestType.DEFEAT_MONSTER.name(), 1,
                        new FirebaseRepository.SimpleCallback() {
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
        // 버프가 이미 적용 중이면 비활성화
        boolean buffAvailable = buffs != null && !buffs.isEmpty();
        boolean noActiveBuffs = !isAttackBuffActive && !isDefenseBuffActive;
        binding.btnBuff.setEnabled(enabled && buffAvailable && noActiveBuffs);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Handler 콜백 정리 (메모리 누수 방지)
        handler.removeCallbacksAndMessages(null);
    }
}
