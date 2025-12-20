package com.example.caloriehunter.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.caloriehunter.R;
import com.example.caloriehunter.data.model.Item;
import com.example.caloriehunter.data.model.Monster;
import com.example.caloriehunter.data.model.User;
import com.example.caloriehunter.data.repository.FirebaseRepository;
import com.example.caloriehunter.databinding.ActivityMainBinding;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * 메인 화면
 * - 유저 정보 표시
 * - 활성 몬스터 표시
 * - 스캔/인벤토리 진입점
 */
public class MainActivity extends AppCompatActivity {

    private static final int LOADING_TIMEOUT_MS = 10000; // 10초 타임아웃

    private ActivityMainBinding binding;
    private FirebaseRepository firebaseRepository;
    private User currentUser;
    private Monster activeMonster;
    private Handler timeoutHandler;
    private boolean isLoadingComplete = false;

    // 배틀 결과 처리
    private final ActivityResultLauncher<Intent> battleLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // 배틀 종료 후 데이터 새로고침
                String userId = firebaseRepository.getCurrentUserId();
                if (userId != null) {
                    // 승리한 경우 메시지 표시
                    if (result.getResultCode() == RESULT_OK) {
                        Toast.makeText(this, "몬스터를 처치했습니다!", Toast.LENGTH_SHORT).show();
                    }
                    // 유저 정보 & 몬스터 정보 갱신
                    loadUserData(userId);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firebaseRepository = FirebaseRepository.getInstance();

        setupClickListeners();
        initializeUser();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 화면 복귀 시 데이터 갱신
        if (currentUser != null) {
            loadActiveMonster();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 타임아웃 핸들러 정리 (메모리 누수 방지)
        if (timeoutHandler != null) {
            timeoutHandler.removeCallbacksAndMessages(null);
        }
    }

    private void setupClickListeners() {
        // 스캔 버튼 (중앙 FAB)
        binding.fabScan.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScanActivity.class);
            startActivity(intent);
        });

        // 출석 버튼
        binding.btnAttendance.setOnClickListener(v -> {
            Intent intent = new Intent(this, AttendanceActivity.class);
            startActivity(intent);
        });

        // 인벤토리 버튼
        binding.btnInventory.setOnClickListener(v -> {
            Intent intent = new Intent(this, InventoryActivity.class);
            startActivity(intent);
        });

        // 전투 버튼
        binding.btnBattle.setOnClickListener(v -> {
            if (activeMonster != null) {
                Intent intent = new Intent(this, BattleActivity.class);
                intent.putExtra("monster_id", activeMonster.getId());
                battleLauncher.launch(intent);
            }
        });

        // 퀘스트 카드
        binding.btnQuest.setOnClickListener(v -> {
            Intent intent = new Intent(this, DailyQuestActivity.class);
            startActivity(intent);
        });

        // 통계 카드
        binding.btnStats.setOnClickListener(v -> {
            Intent intent = new Intent(this, NutritionStatsActivity.class);
            startActivity(intent);
        });

        // 하단 네비게이션 - 홈 (현재 화면)
        binding.navHome.setOnClickListener(v -> {
            // 이미 홈이므로 스크롤 맨 위로
        });

        // 하단 네비게이션 - 기록
        binding.navHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, NutritionStatsActivity.class);
            startActivity(intent);
        });
    }

    private void initializeUser() {
        showLoading(true);
        isLoadingComplete = false;

        // 타임아웃 설정 (10초 후에도 로딩 중이면 에러 표시)
        timeoutHandler = new Handler(Looper.getMainLooper());
        timeoutHandler.postDelayed(() -> {
            if (!isLoadingComplete) {
                showLoading(false);
                Toast.makeText(MainActivity.this,
                    "연결 시간 초과. 인터넷 연결을 확인해주세요.", Toast.LENGTH_LONG).show();
            }
        }, LOADING_TIMEOUT_MS);

        String userId = firebaseRepository.getCurrentUserId();

        // [수정] 로그인이 되어 있으면 데이터 로드, 아니면 로그인 화면으로 쫓아내기
        if (userId != null) {
            loadUserData(userId);
        } else {
            // 로그인이 풀렸거나 안 된 상태 -> 로그인 화면으로 이동
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
            finish(); // 메인 화면 종료
        }
    }

    private void createNewUser(String userId) {
        String nickname = "헌터" + userId.substring(0, 4).toUpperCase();

        firebaseRepository.getOrCreateUser(userId, nickname, new FirebaseRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                currentUser = user;
                isLoadingComplete = true;
                runOnUiThread(() -> {
                    updateUserUI();
                    loadActiveMonster();
                    showLoading(false);
                });
            }

            @Override
            public void onError(String message) {
                isLoadingComplete = true;
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(MainActivity.this, "유저 생성 실패: " + message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadUserData(String userId) {
        firebaseRepository.getOrCreateUser(userId, "", new FirebaseRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                currentUser = user;
                isLoadingComplete = true;
                runOnUiThread(() -> {
                    updateUserUI();
                    loadActiveMonster();
                    showLoading(false);
                });
            }

            @Override
            public void onError(String message) {
                isLoadingComplete = true;
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(MainActivity.this, "데이터 로드 실패: " + message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadActiveMonster() {
        String userId = firebaseRepository.getCurrentUserId();
        if (userId == null) return;

        firebaseRepository.getActiveMonster(userId, new FirebaseRepository.MonsterCallback() {
            @Override
            public void onSuccess(Monster monster) {
                activeMonster = monster;
                runOnUiThread(() -> {
                    updateMonsterUI();
                    loadMonsterQueueSize();
                });
            }

            @Override
            public void onError(String message) {
                activeMonster = null;
                runOnUiThread(() -> {
                    showEmptyState();
                    binding.tvMonsterQueue.setVisibility(View.GONE);
                });
            }
        });
    }

    private void loadMonsterQueueSize() {
        String userId = firebaseRepository.getCurrentUserId();
        if (userId == null) return;

        firebaseRepository.getMonsterQueueSize(userId, new FirebaseRepository.MonsterQueueCallback() {
            @Override
            public void onSuccess(int queueSize) {
                runOnUiThread(() -> {
                    if (queueSize > 1) {
                        binding.tvMonsterQueue.setVisibility(View.VISIBLE);
                        binding.tvMonsterQueue.setText("🔥 남은 몬스터: " + queueSize + "마리");
                    } else {
                        binding.tvMonsterQueue.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> binding.tvMonsterQueue.setVisibility(View.GONE));
            }
        });
    }

    private void updateUserUI() {
        if (currentUser == null) return;

        binding.tvLevel.setText(String.valueOf(currentUser.getLevel()));
        binding.tvNickname.setText(currentUser.getNickname());

        // HP 바
        int hpPercent = (int) ((float) currentUser.getHp() / currentUser.getMaxHp() * 100);
        binding.progressHp.setProgress(hpPercent);
        binding.tvHpValue.setText(currentUser.getHp() + "/" + currentUser.getMaxHp());

        // EXP 바
        int expPercent = (int) ((float) currentUser.getExp() / currentUser.getExpToNextLevel() * 100);
        binding.progressExp.setProgress(expPercent);
        binding.tvExpValue.setText(currentUser.getExp() + "/" + currentUser.getExpToNextLevel());

        // 장착 무기 정보
        if (currentUser.getEquippedWeaponName() != null && !currentUser.getEquippedWeaponName().isEmpty()) {
            binding.tvEquippedWeapon.setText(currentUser.getEquippedWeaponName() + " (+" + currentUser.getEquippedWeaponPower() + ")");
            binding.tvEquippedWeapon.setTextColor(getColor(R.color.primary));
            // 무기 내구도 로드
            loadWeaponDurability();
        } else {
            binding.tvEquippedWeapon.setText("장착된 무기 없음");
            binding.tvEquippedWeapon.setTextColor(getColor(R.color.text_secondary));
            binding.durabilityBar.setVisibility(View.GONE);
        }

        // 총 공격력
        binding.tvTotalAttack.setText(String.valueOf(currentUser.getTotalAttackPower()));
    }

    private void loadWeaponDurability() {
        String userId = firebaseRepository.getCurrentUserId();
        if (userId == null || currentUser == null || currentUser.getEquippedWeaponId() == null) {
            binding.durabilityBar.setVisibility(View.GONE);
            return;
        }

        firebaseRepository.getWeapon(userId, currentUser.getEquippedWeaponId(), new FirebaseRepository.ItemCallback() {
            @Override
            public void onSuccess(Item weapon) {
                runOnUiThread(() -> {
                    if (weapon.getMaxDurability() > 0) {
                        binding.durabilityBar.setVisibility(View.VISIBLE);
                        binding.progressWeaponDurability.setProgress(weapon.getDurabilityPercent());
                        binding.tvWeaponDurability.setText(weapon.getDurability() + "/" + weapon.getMaxDurability());
                    } else {
                        binding.durabilityBar.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> binding.durabilityBar.setVisibility(View.GONE));
            }
        });
    }

    private void updateMonsterUI() {
        if (activeMonster == null) {
            showEmptyState();
            return;
        }

        // 빈 상태 숨기고 몬스터 표시
        binding.emptyState.setVisibility(View.GONE);
        binding.monsterState.setVisibility(View.VISIBLE);

        // 티어
        binding.tvMonsterTier.setText(activeMonster.getTier().toUpperCase());
        binding.tvMonsterTier.setTextColor(android.graphics.Color.parseColor(activeMonster.getTierColor()));

        // 이름
        binding.tvMonsterName.setText(activeMonster.getName());

        // 이모지
        binding.tvMonsterEmoji.setText(activeMonster.getElementEmoji());

        // HP
        binding.tvMonsterHp.setText(activeMonster.getHp() + " / " + activeMonster.getMaxHp());
        int hpPercent = (int) ((float) activeMonster.getHp() / activeMonster.getMaxHp() * 100);
        binding.progressMonsterHp.setProgress(hpPercent);

        // 스탯
        binding.tvMonsterDef.setText(String.valueOf(activeMonster.getDefense()));
        binding.tvMonsterAtk.setText(String.valueOf(activeMonster.getAttack()));
        binding.tvMonsterPsn.setText(String.valueOf(activeMonster.getPoisonDamage()));
    }

    private void showEmptyState() {
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.monsterState.setVisibility(View.GONE);
    }

    private void showLoading(boolean show) {
        binding.loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
