/**
 * Chat Notification Function for Amora App (Gen2)
 * Trigger: Realtime Database value created at /chats/{roomId}/{messageId}
 */

const {onValueCreated} = require("firebase-functions/v2/database");
const admin = require("firebase-admin");

// Initialize Firebase Admin SDK ONCE
if (!admin.apps.length) {
    admin.initializeApp();
}

/**
 * onChatMessageCreated (2nd gen)
 * Trigger path: /chats/{roomId}/{messageId}
 */
exports.onChatMessageCreated = onValueCreated("/chats/{roomId}/{messageId}", async (event) => {
    const message = event.data.val();
    const roomId = event.params.roomId;

    if (!message) {
        console.log("No message data found");
        return;
    }

    const senderId = message.senderId;
    const receiverId = message.receiverId;
    const text = message.text || "";

    if (!senderId || !receiverId) {
        console.log("Missing senderId or receiverId", message);
        return;
    }

    // Avoid notifying yourself (safety)
    if (senderId === receiverId) {
        console.log("Sender = receiver, skipping notification.");
        return;
    }

    // 1) Fetch receiver FCM token: /users/{uid}/fcmToken
    const receiverTokenSnap = await admin.database()
        .ref(`/users/${receiverId}/fcmToken`)
        .get();

    const receiverToken = receiverTokenSnap.val();

    if (!receiverToken) {
        console.log(`No FCM token for user ${receiverId}`);
        return;
    }

    // 2) Fetch sender profile (name + photo)
    const senderSnap = await admin.database()
        .ref(`/users/${senderId}`)
        .get();

    const sender = senderSnap.val() || {};
    const senderName = sender.fullName || "New message";
    const senderPhotoUrl = sender.photoUrl || "";

    // Shorten long text for notification body
    const body =
        text.length > 50 ? text.substring(0, 47) + "..." : (text || "Sent you a message");

    // Build FCM message using new Admin SDK API
    const notificationMessage = {
        token: receiverToken,
        notification: {
            title: senderName,
            body: body,
        },
        data: {
            type: "chat",
            roomId: roomId,
            senderId: senderId,
            senderName: senderName,
            senderPhotoUrl: senderPhotoUrl,
        },
        android: {
            priority: "high",
        },
    };

    try {
        const response = await admin.messaging().send(notificationMessage);
        console.log("Notification sent successfully:", response);
    } catch (err) {
        console.error("Error sending notification:", err);
    }
});
