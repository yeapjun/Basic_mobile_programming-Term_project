package com.example.termproject;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;

import com.bumptech.glide.Glide;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.firestore.DocumentChange.Type;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;


import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;

    // UI 요소
    private GoogleMap mMap;
    private ProgressBar progressBar;
    private FloatingActionButton fabAddPhoto;

    // Firebase 인스턴스
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private ListenerRegistration firestoreListener;

    // 위치 서비스
    private FusedLocationProviderClient fusedLocationClient;
    private double currentLat = 37.5665;  // 기본값: 서울
    private double currentLng = 126.9780;

    // ML Kit 이미지 라벨러
    private ImageLabeler imageLabeler;

    // 마커-데이터 매핑 (InfoWindow 표시용)
    private Map<Marker, PlaceModel> markerDataMap = new HashMap<>();

    // 갤러리 선택 결과 처리
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        processSelectedImage(imageUri);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initFirebase();
        initMLKit();
        initLocationClient();
        checkPermissions();
    }

    // View 초기화
    private void initViews() {
        progressBar = findViewById(R.id.progress_bar);
        fabAddPhoto = findViewById(R.id.fab_add_photo);
        fabAddPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openGallery();
            }
        });
        fabAddPhoto.setEnabled(false);
    }

    // Firebase 초기화
    private void initFirebase() {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // 익명 로그인 수행
        signInAnonymously();
    }

    // ML Kit 이미지 라벨러 초기화
    private void initMLKit() {
        ImageLabelerOptions options = new ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.7f)  // 신뢰도 70% 이상만
                .build();
        imageLabeler = ImageLabeling.getClient(options);
    }

    private void initLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    // Firebase 익명 인증
    private void signInAnonymously() {
        showLoading(true);
        mAuth.signInAnonymously()
                .addOnCompleteListener(this, task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        Log.d(TAG, "익명 로그인 성공: " + user.getUid());
                        fabAddPhoto.setEnabled(true);
                        initMap();
                    } else {
                        Log.e(TAG, "익명 로그인 실패", task.getException());
                        Toast.makeText(this, "로그인 실패", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // 지도 초기화
    private void initMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // 커스텀 InfoWindow 어댑터 설정
        mMap.setInfoWindowAdapter(new CustomInfoWindowAdapter());

        // 위치 권한이 있으면 현재 위치로 이동
        if (hasLocationPermission()) {
            enableMyLocation();
        }

        // Firestore 실시간 리스너 시작
        startFirestoreListener();
    }

    // 커스텀 InfoWindow 어댑터 - 마커 클릭 시 이미지와 카테고리 표시
    private class CustomInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {
        private final View infoWindow;

        CustomInfoWindowAdapter() {
            infoWindow = LayoutInflater.from(MainActivity.this)
                    .inflate(R.layout.custom_info_window, null);
        }

        @Override
        public View getInfoWindow(@NonNull Marker marker) {
            return null;  // 기본 프레임 사용
        }

        @Override
        public View getInfoContents(@NonNull Marker marker) {
            PlaceModel place = markerDataMap.get(marker);
            if (place == null) return null;

            ImageView imageView = infoWindow.findViewById(R.id.info_image);
            TextView categoryText = infoWindow.findViewById(R.id.info_category);
            TextView timeText = infoWindow.findViewById(R.id.info_time);

            // Glide로 이미지 로드
            Glide.with(MainActivity.this)
                    .load(place.getImageUrl())
                    .centerCrop()
                    .into(imageView);

            // 카테고리 한글 표시
            String categoryKor = getCategoryKorean(place.getCategory());
            categoryText.setText("AI 분류: " + categoryKor);

            // 시간 포맷
            if (place.getTimestamp() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA);
                timeText.setText(sdf.format(place.getTimestamp().toDate()));
            }

            return infoWindow;
        }
    }

    private String getCategoryKorean(String category) {
        switch (category) {
            case "Food": return "음식";
            case "Nature": return "자연";
            default: return "일반";
        }
    }

    // Firestore 실시간 리스너 - 데이터 변경 감지
    private void startFirestoreListener() {
        firestoreListener = db.collection("places")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Firestore 리스너 오류", error);
                        return;
                    }
                    if (snapshots == null) return;

                    // 문서 변경 사항 처리
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        PlaceModel place = dc.getDocument().toObject(PlaceModel.class);
                        place.setDocumentId(dc.getDocument().getId());

                        switch (dc.getType()) {
                            case ADDED:
                                addMarkerToMap(place);
                                break;
                            case REMOVED:
                                removeMarkerFromMap(place.getDocumentId());
                                break;
                        }
                    }
                });
    }

    // 지도에 마커 추가 - 카테고리별 색상 구분
    private void addMarkerToMap(PlaceModel place) {
        if (mMap == null) return;

        LatLng position = new LatLng(place.getLatitude(), place.getLongitude());

        // 카테고리별 마커 색상 설정
        float markerColor;
        switch (place.getCategory()) {
            case "Food":
                markerColor = BitmapDescriptorFactory.HUE_ORANGE;
                break;
            case "Nature":
                markerColor = BitmapDescriptorFactory.HUE_GREEN;
                break;
            default:
                markerColor = BitmapDescriptorFactory.HUE_RED;
                break;
        }

        MarkerOptions options = new MarkerOptions()
                .position(position)
                .title(getCategoryKorean(place.getCategory()))
                .icon(BitmapDescriptorFactory.defaultMarker(markerColor));

        Marker marker = mMap.addMarker(options);
        if (marker != null) {
            marker.setTag(place.getDocumentId());
            markerDataMap.put(marker, place);
        }
    }

    private void removeMarkerFromMap(String documentId) {
        for (Map.Entry<Marker, PlaceModel> entry : markerDataMap.entrySet()) {
            if (entry.getValue().getDocumentId().equals(documentId)) {
                entry.getKey().remove();
                markerDataMap.remove(entry.getKey());
                break;
            }
        }
    }

    // 갤러리 열기
    private void openGallery() {
        if (!hasMediaPermission()) {
            requestMediaPermission();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    // 선택된 이미지 처리 (핵심 로직)
    private void processSelectedImage(Uri imageUri) {
        showLoading(true);

        try {
            // 1단계: 사진에서 GPS 좌표 추출
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            ExifInterface exif = new ExifInterface(inputStream);
            double[] latLong = exif.getLatLong();
            inputStream.close();

            final double photoLat;
            final double photoLng;

            if (latLong != null) {
                // 사진에 GPS 정보가 있는 경우
                photoLat = latLong[0];
                photoLng = latLong[1];
                Log.d(TAG, "사진 GPS 좌표: " + photoLat + ", " + photoLng);
            } else {
                // GPS 정보가 없으면 현재 디바이스 위치 사용
                photoLat = currentLat;
                photoLng = currentLng;
                Log.d(TAG, "GPS 없음, 현재 위치 사용: " + photoLat + ", " + photoLng);
            }

            // 2단계: ML Kit으로 이미지 분석
            analyzeImageWithMLKit(imageUri, photoLat, photoLng);

        } catch (Exception e) {
            Log.e(TAG, "이미지 처리 오류", e);
            showLoading(false);
            Toast.makeText(this, "이미지 처리 실패", Toast.LENGTH_SHORT).show();
        }
    }

    // ML Kit 이미지 라벨링 분석
    private void analyzeImageWithMLKit(Uri imageUri, double lat, double lng) {
        try {
            InputImage inputImage = InputImage.fromFilePath(this, imageUri);

            imageLabeler.process(inputImage)
                    .addOnSuccessListener(labels -> {
                        // AI 분석 결과로 카테고리 결정
                        String category = determineCategory(labels);
                        Log.d(TAG, "AI 분류 결과: " + category);

                        // 3단계: Storage에 업로드
                        uploadToStorage(imageUri, lat, lng, category);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "ML Kit 분석 실패", e);
                        // 실패해도 General로 업로드 진행
                        uploadToStorage(imageUri, lat, lng, "General");
                    });
        } catch (Exception e) {
            Log.e(TAG, "InputImage 생성 실패", e);
            showLoading(false);
        }
    }

    // ML Kit 라벨 결과로 카테고리 분류
    private String determineCategory(List<ImageLabel> labels) {
        for (ImageLabel label : labels) {
            String text = label.getText().toLowerCase();
            Log.d(TAG, "감지된 라벨: " + label.getText() + " (" + label.getConfidence() + ")");

            // 음식 관련 키워드
            if (text.contains("food") || text.contains("meal") || text.contains("dish") ||
                    text.contains("cuisine") || text.contains("restaurant") || text.contains("breakfast") ||
                    text.contains("lunch") || text.contains("dinner") || text.contains("snack")) {
                return "Food";
            }

            // 자연 관련 키워드
            if (text.contains("nature") || text.contains("mountain") || text.contains("sky") ||
                    text.contains("beach") || text.contains("ocean") || text.contains("forest") ||
                    text.contains("tree") || text.contains("flower") || text.contains("landscape") ||
                    text.contains("sunset") || text.contains("lake") || text.contains("river")) {
                return "Nature";
            }
        }
        return "General";
    }

    // Firebase Storage에 이미지 업로드
    private void uploadToStorage(Uri imageUri, double lat, double lng, String category) {
        String fileName = "images/" + UUID.randomUUID().toString() + ".jpg";
        StorageReference ref = storage.getReference().child(fileName);

        ref.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    // 업로드 성공 후 다운로드 URL 획득
                    ref.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                        // 4단계: Firestore에 메타데이터 저장
                        saveToFirestore(lat, lng, downloadUri.toString(), category);
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Storage 업로드 실패", e);
                    showLoading(false);
                    Toast.makeText(this, "업로드 실패", Toast.LENGTH_SHORT).show();
                });
    }

    // Firestore에 장소 데이터 저장
    private void saveToFirestore(double lat, double lng, String imageUrl, String category) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            showLoading(false);
            return;
        }

        Map<String, Object> placeData = new HashMap<>();
        placeData.put("uid", user.getUid());
        placeData.put("latitude", lat);
        placeData.put("longitude", lng);
        placeData.put("imageUrl", imageUrl);
        placeData.put("category", category);
        placeData.put("timestamp", Timestamp.now());

        db.collection("places")
                .add(placeData)
                .addOnSuccessListener(docRef -> {
                    Log.d(TAG, "Firestore 저장 완료: " + docRef.getId());
                    showLoading(false);
                    Toast.makeText(this, "저장 완료! (분류: " + getCategoryKorean(category) + ")",
                            Toast.LENGTH_SHORT).show();

                    // 저장된 위치로 카메라 이동
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), 15));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firestore 저장 실패", e);
                    showLoading(false);
                    Toast.makeText(this, "저장 실패", Toast.LENGTH_SHORT).show();
                });
    }

    // 권한 관련 메서드들
    private void checkPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_MEDIA_IMAGES
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }

        boolean allGranted = true;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasMediaPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestMediaPermission() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        ActivityCompat.requestPermissions(this, new String[]{permission}, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (hasLocationPermission() && mMap != null) {
                enableMyLocation();
            }
        }
    }

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        mMap.setMyLocationEnabled(true);

        // 현재 위치 가져와서 카메라 이동
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLat = location.getLatitude();
                        currentLng = location.getLongitude();
                        LatLng currentPos = new LatLng(currentLat, currentLng);
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPos, 15));
                    }
                });
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        fabAddPhoto.setEnabled(!show);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Firestore 리스너 해제
        if (firestoreListener != null) {
            firestoreListener.remove();
        }
    }
}