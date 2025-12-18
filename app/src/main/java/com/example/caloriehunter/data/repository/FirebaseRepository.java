package com.example.caloriehunter.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.caloriehunter.data.model.BattleLog;
import com.example.caloriehunter.data.model.Item;
import com.example.caloriehunter.data.model.Monster;
import com.example.caloriehunter.data.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Firebase Realtime Database 레포지토리
 */
public class FirebaseRepository {

    private static final String TAG = "FirebaseRepository";
    private static FirebaseRepository instance;

    private final FirebaseAuth auth;
    private final DatabaseReference database;

    // 콜백 인터페이스
    public interface AuthCallback {
        void onSuccess(String userId);
        void onError(String message);
    }

    public interface UserCallback {
        void onSuccess(User user);
        void onError(String message);
    }

    public interface MonsterCallback {
        void onSuccess(Monster monster);
        void onError(String message);
    }

    public interface MonstersCallback {
        void onSuccess(List<Monster> monsters);
        void onError(String message);
    }

    public interface ItemCallback {
        void onSuccess(Item item);
        void onError(String message);
    }

    public interface ItemsCallback {
        void onSuccess(List<Item> items);
        void onError(String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    private FirebaseRepository() {
        auth = FirebaseAuth.getInstance();
        // Database URL을 명시적으로 지정 (google-services.json에 firebase_url이 없는 경우 필요)
        database = FirebaseDatabase.getInstance("https://term-project-a8065-default-rtdb.asia-southeast1.firebasedatabase.app").getReference();
    }

    public static synchronized FirebaseRepository getInstance() {
        if (instance == null) {
            instance = new FirebaseRepository();
        }
        return instance;
    }

    // ========== 인증 ==========

    /**
     * 현재 로그인된 유저 ID
     */
    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    /**
     * 익명 로그인
     */
    public void signInAnonymously(AuthCallback callback) {
        auth.signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    Log.d(TAG, "Anonymous sign in success: " + uid);
                    callback.onSuccess(uid);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Anonymous sign in failed", e);
                    callback.onError(e.getMessage());
                });
    }

    // ========== 유저 ==========

    /**
     * 유저 생성 또는 조회
     */
    public void getOrCreateUser(String uid, String nickname, UserCallback callback) {
        DatabaseReference userRef = database.child("users").child(uid);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // 기존 유저
                    User user = snapshot.getValue(User.class);
                    if (user != null) {
                        user.setLastLoginAt(System.currentTimeMillis());
                        userRef.child("lastLoginAt").setValue(user.getLastLoginAt());
                        callback.onSuccess(user);
                    } else {
                        callback.onError("유저 데이터 파싱 실패");
                    }
                } else {
                    // 신규 유저 생성
                    User newUser = User.createNewUser(uid, nickname);
                    userRef.setValue(newUser.toMap())
                            .addOnSuccessListener(aVoid -> callback.onSuccess(newUser))
                            .addOnFailureListener(e -> callback.onError(e.getMessage()));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * 유저 정보 업데이트
     */
    public void updateUser(User user, SimpleCallback callback) {
        database.child("users").child(user.getUid())
                .updateChildren(user.toMap())
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ========== 몬스터 ==========

    /**
     * 몬스터 저장
     */
    public void saveMonster(Monster monster, MonsterCallback callback) {
        String monsterId = monster.getId();
        database.child("monsters").child(monsterId)
                .setValue(monster.toMap())
                .addOnSuccessListener(aVoid -> {
                    // 유저의 활성 몬스터로 설정
                    database.child("users").child(monster.getOwnerId())
                            .child("activeMonsterId").setValue(monsterId);
                    callback.onSuccess(monster);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * 유저의 활성 몬스터 조회
     */
    public void getActiveMonster(String userId, MonsterCallback callback) {
        database.child("users").child(userId).child("activeMonsterId")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String monsterId = snapshot.getValue(String.class);
                        if (monsterId == null) {
                            callback.onError("활성 몬스터 없음");
                            return;
                        }

                        database.child("monsters").child(monsterId)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        Monster monster = snapshot.getValue(Monster.class);
                                        if (monster != null && !"defeated".equals(monster.getStatus())) {
                                            callback.onSuccess(monster);
                                        } else {
                                            // 처치된 몬스터거나 데이터 없음 - activeMonsterId 제거
                                            database.child("users").child(userId).child("activeMonsterId").removeValue();
                                            callback.onError("활성 몬스터 없음");
                                        }
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        callback.onError(error.getMessage());
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError(error.getMessage());
                    }
                });
    }

    /**
     * 몬스터 HP 업데이트 (전투 시)
     */
    public void updateMonsterHp(String monsterId, int newHp, SimpleCallback callback) {
        database.child("monsters").child(monsterId).child("hp").setValue(newHp)
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * 몬스터 처치 처리
     */
    public void defeatMonster(String monsterId, String ownerId, SimpleCallback callback) {
        database.child("monsters").child(monsterId).child("status").setValue("defeated")
                .addOnSuccessListener(aVoid -> {
                    // 유저의 활성 몬스터 제거
                    database.child("users").child(ownerId).child("activeMonsterId").removeValue();
                    // 처치 수 증가
                    database.child("users").child(ownerId).child("totalMonstersKilled")
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    Integer count = snapshot.getValue(Integer.class);
                                    database.child("users").child(ownerId)
                                            .child("totalMonstersKilled").setValue((count != null ? count : 0) + 1);
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {}
                            });
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ========== 아이템 ==========

    /**
     * 아이템 저장 (인벤토리에 추가)
     */
    public void saveItem(Item item, ItemCallback callback) {
        String path;
        switch (item.getType()) {
            case WEAPON:
                path = "inventory/weapons";
                break;
            case POTION:
                path = "inventory/potions";
                break;
            default:
                path = "inventory/buffs";
                break;
        }

        database.child("users").child(item.getOwnerId())
                .child(path).child(item.getId())
                .setValue(item.toMap())
                .addOnSuccessListener(aVoid -> callback.onSuccess(item))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * 무기 목록 조회
     */
    public void getWeapons(String userId, ItemsCallback callback) {
        database.child("users").child(userId).child("inventory/weapons")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<Item> items = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Item item = child.getValue(Item.class);
                            if (item != null) {
                                item.setType(Item.ItemType.WEAPON);
                                items.add(item);
                            }
                        }
                        callback.onSuccess(items);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError(error.getMessage());
                    }
                });
    }

    /**
     * 포션 목록 조회
     */
    public void getPotions(String userId, ItemsCallback callback) {
        database.child("users").child(userId).child("inventory/potions")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<Item> items = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Item item = child.getValue(Item.class);
                            if (item != null) {
                                item.setType(Item.ItemType.POTION);
                                items.add(item);
                            }
                        }
                        callback.onSuccess(items);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError(error.getMessage());
                    }
                });
    }

    /**
     * 아이템 사용 (수량 감소)
     */
    public void useItem(String userId, Item item, SimpleCallback callback) {
        String path;
        switch (item.getType()) {
            case WEAPON:
                path = "inventory/weapons";
                break;
            case POTION:
                path = "inventory/potions";
                break;
            default:
                path = "inventory/buffs";
                break;
        }

        DatabaseReference itemRef = database.child("users").child(userId)
                .child(path).child(item.getId());

        if (item.getQuantity() <= 1) {
            // 마지막 아이템 → 삭제
            itemRef.removeValue()
                    .addOnSuccessListener(aVoid -> callback.onSuccess())
                    .addOnFailureListener(e -> callback.onError(e.getMessage()));
        } else {
            // 수량 감소
            itemRef.child("quantity").setValue(item.getQuantity() - 1)
                    .addOnSuccessListener(aVoid -> callback.onSuccess())
                    .addOnFailureListener(e -> callback.onError(e.getMessage()));
        }
    }

    // ========== 전투 기록 ==========

    /**
     * 전투 기록 콜백
     */
    public interface BattleLogsCallback {
        void onSuccess(List<BattleLog> logs);
        void onError(String message);
    }

    /**
     * 전투 기록 저장
     */
    public void saveBattleLog(BattleLog log, SimpleCallback callback) {
        database.child("users").child(log.getOderId())
                .child("battleLogs").child(log.getId())
                .setValue(log.toMap())
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * 전투 기록 조회 (최근 50개)
     */
    public void getBattleLogs(String userId, BattleLogsCallback callback) {
        database.child("users").child(userId).child("battleLogs")
                .orderByChild("timestamp")
                .limitToLast(50)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<BattleLog> logs = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            BattleLog log = child.getValue(BattleLog.class);
                            if (log != null) {
                                logs.add(log);
                            }
                        }
                        // 최신순 정렬
                        logs.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
                        callback.onSuccess(logs);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError(error.getMessage());
                    }
                });
    }
}
