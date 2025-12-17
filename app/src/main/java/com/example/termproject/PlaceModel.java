package com.example.termproject;

import com.google.firebase.Timestamp;

/**
 * Firestore 'places' 컬렉션과 매핑되는 데이터 모델
 */
public class PlaceModel {
    private String uid;
    private double latitude;
    private double longitude;
    private String imageUrl;
    private String category;
    private Timestamp timestamp;
    private String documentId;

    // Firestore 역직렬화를 위한 기본 생성자
    public PlaceModel() {}

    public PlaceModel(String uid, double latitude, double longitude,
                      String imageUrl, String category, Timestamp timestamp) {
        this.uid = uid;
        this.latitude = latitude;
        this.longitude = longitude;
        this.imageUrl = imageUrl;
        this.category = category;
        this.timestamp = timestamp;
    }

    // Getter & Setter
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
}
