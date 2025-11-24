package com.retroxinteractive.amora;

public class UserProfile {

    private String uid;
    private String name;
    private String profileUrl;
    private String distance;
    private long matchScore;

    public UserProfile() { }

    public UserProfile(String uid, String name, String profileUrl, String distance, long matchScore) {
        this.uid = uid;
        this.name = name;
        this.profileUrl = profileUrl;
        this.distance = distance;
        this.matchScore = matchScore;
    }

    // --- GETTERS ---
    public String getUid() {
        return uid;
    }

    public String getName() {
        return name;
    }

    public String getProfileUrl() {
        return profileUrl;
    }

    public String getDistance() {
        return distance;
    }

    public long getMatchScore() {
        return matchScore;
    }

    // --- SETTERS (optional but good to have) ---
    public void setUid(String uid) {
        this.uid = uid;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setProfileUrl(String profileUrl) {
        this.profileUrl = profileUrl;
    }

    public void setDistance(String distance) {
        this.distance = distance;
    }

    public void setMatchScore(long matchScore) {
        this.matchScore = matchScore;
    }
}
