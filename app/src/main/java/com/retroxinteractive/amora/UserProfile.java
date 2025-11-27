package com.retroxinteractive.amora;

import java.util.List;

public class UserProfile {

    // These field names should match what you store in Realtime Database
    private String uid;
    private String name;
    String age;
    private String bio;
    private String address;
    private String photoUrl;
    private Boolean verified;
    private Double distanceKm;       // optional; can be null
    private Integer matchPercent;    // optional
    private List<String> interests;  // optional

    // Required empty constructor for Firebase
    public UserProfile() {
    }

    // --- Getters & setters ---

    public String getAddress() { return address; }

    public String getAge() { return age; }
    public void setAge(String age) { this.age = age; }
    public void setAddress(String address) { this.address = address; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public Boolean getVerified() { return verified != null && verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }

    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }

    public Integer getMatchPercent() { return matchPercent; }
    public void setMatchPercent(Integer matchPercent) { this.matchPercent = matchPercent; }

    public List<String> getInterests() { return interests; }
    public void setInterests(List<String> interests) { this.interests = interests; }
}