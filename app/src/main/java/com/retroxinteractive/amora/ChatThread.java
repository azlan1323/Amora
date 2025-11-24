package com.retroxinteractive.amora;

public class ChatThread {

    public String roomId;
    public String otherUserId;
    public String otherUserName;
    public String otherUserPhotoUrl;
    public String lastMessage;
    public long lastTimestamp;
    public long unreadCount;

    public ChatThread() {
        // required for Firebase
    }

    public ChatThread(String roomId,
                      String otherUserId,
                      String otherUserName,
                      String otherUserPhotoUrl,
                      String lastMessage,
                      long lastTimestamp,
                      long unreadCount) {
        this.roomId = roomId;
        this.otherUserId = otherUserId;
        this.otherUserName = otherUserName;
        this.otherUserPhotoUrl = otherUserPhotoUrl;
        this.lastMessage = lastMessage;
        this.lastTimestamp = lastTimestamp;
        this.unreadCount = unreadCount;
    }
}