package com.retroxinteractive.amora;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChatFragment extends Fragment {

    private static final String ARG_RECEIVER_ID = "receiverId";
    private static final String ARG_RECEIVER_NAME = "receiverName";
    private static final String ARG_RECEIVER_PHOTO = "receiverPhotoUrl";

    private ImageView btnBack, btnSend;
    private CircleImageView imgProfile;
    private TextView txtName, txtStatus;
    private EditText edtMessage;
    private RecyclerView recyclerView;

    private MessageAdapter adapter;
    private List<Message> messageList = new ArrayList<>();

    private FirebaseUser currentUser;
    private DatabaseReference chatRef;
    private DatabaseReference userChatsRoot;

    private String receiverId;
    private String receiverName;
    private String receiverPhotoUrl;
    private String roomId;

    public ChatFragment() {
        // Required empty constructor
    }

    public static ChatFragment newInstance(String receiverId,
                                           String receiverName,
                                           String receiverPhotoUrl) {
        ChatFragment fragment = new ChatFragment();
        Bundle args = new Bundle();
        args.putString(ARG_RECEIVER_ID, receiverId);
        args.putString(ARG_RECEIVER_NAME, receiverName);
        args.putString(ARG_RECEIVER_PHOTO, receiverPhotoUrl);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (getArguments() != null) {
            receiverId = getArguments().getString(ARG_RECEIVER_ID);
            receiverName = getArguments().getString(ARG_RECEIVER_NAME);
            receiverPhotoUrl = getArguments().getString(ARG_RECEIVER_PHOTO);
        }

        userChatsRoot = FirebaseDatabase.getInstance().getReference("userChats");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_chat, container, false);

        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show();
            return root;
        }

        initViews(root);
        setupToolbar();
        setupRecycler();

        if (!TextUtils.isEmpty(receiverId)) {
            String myId = currentUser.getUid();
            if (myId.compareTo(receiverId) < 0) {
                roomId = myId + "_" + receiverId;
            } else {
                roomId = receiverId + "_" + myId;
            }

            chatRef = FirebaseDatabase.getInstance()
                    .getReference("chats")
                    .child(roomId);

            listenForMessages();
            markThreadAsRead();   // clear unread count when opening

            btnSend.setOnClickListener(v -> sendMessage());
        } else {
            btnSend.setOnClickListener(v ->
                    Toast.makeText(requireContext(),
                            "No chat selected", Toast.LENGTH_SHORT).show());
        }

        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());

        return root;
    }

    private void initViews(View root) {
        btnBack = root.findViewById(R.id.btnBack);
        btnSend = root.findViewById(R.id.btnSend);
        imgProfile = root.findViewById(R.id.imgProfile);
        txtName = root.findViewById(R.id.txtName);
        txtStatus = root.findViewById(R.id.txtStatus);
        edtMessage = root.findViewById(R.id.edtMessage);
        recyclerView = root.findViewById(R.id.recyclerMessages);

        messageList = new ArrayList<>();
    }

    private void setupToolbar() {
        if (txtName == null || txtStatus == null) return;

        String title = !TextUtils.isEmpty(receiverName) ? receiverName : "Chat";
        txtName.setText(title);
        txtStatus.setText(""); // placeholder for status

        if (!TextUtils.isEmpty(receiverPhotoUrl)) {
            Glide.with(requireContext())
                    .load(receiverPhotoUrl)
                    .placeholder(R.drawable.ic_nav_profile)
                    .into(imgProfile);
        } else {
            imgProfile.setImageResource(R.drawable.ic_nav_profile);
        }
    }

    private void setupRecycler() {
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);
        recyclerView.setLayoutManager(lm);
        adapter = new MessageAdapter(messageList);
        recyclerView.setAdapter(adapter);
    }

    private void sendMessage() {
        if (chatRef == null) return;

        String text = edtMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        String id = chatRef.push().getKey();
        if (id == null) return;

        long timestamp = System.currentTimeMillis();

        Message message = new Message(
                id,
                currentUser.getUid(),
                receiverId,
                text,
                timestamp
        );

        chatRef.child(id).setValue(message)
                .addOnSuccessListener(unused -> {
                    edtMessage.setText("");
                    scrollToBottom();
                    updateThreadsOnSend(text, timestamp);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(),
                                "Failed to send", Toast.LENGTH_SHORT).show());
    }

    private void updateThreadsOnSend(String lastMessage, long timestamp) {
        // Sender side thread: unread = 0
        ChatThread senderThread = new ChatThread(
                roomId,
                receiverId,
                receiverName,
                receiverPhotoUrl,
                lastMessage,
                timestamp,
                0
        );

        userChatsRoot.child(currentUser.getUid())
                .child(roomId)
                .setValue(senderThread);

        // Receiver side thread: increment unreadCount
        DatabaseReference receiverThreadRef = userChatsRoot
                .child(receiverId)
                .child(roomId);

        Map<String, Object> update = new HashMap<>();
        update.put("roomId", roomId);
        update.put("otherUserId", currentUser.getUid());
        update.put("otherUserName",
                currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "You");
        update.put("otherUserPhotoUrl", ""); // can be filled from user profile later
        update.put("lastMessage", lastMessage);
        update.put("lastTimestamp", timestamp);
        update.put("unreadCount", ServerValue.increment(1));

        receiverThreadRef.updateChildren(update);
    }

    private void markThreadAsRead() {
        if (TextUtils.isEmpty(roomId)) return;

        userChatsRoot.child(currentUser.getUid())
                .child(roomId)
                .child("unreadCount")
                .setValue(0);
    }

    private void listenForMessages() {
        chatRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                Message msg = snapshot.getValue(Message.class);
                if (msg != null) {
                    messageList.add(msg);
                    adapter.notifyItemInserted(messageList.size() - 1);
                    scrollToBottom();
                }
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, String prev) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, String prev) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void scrollToBottom() {
        recyclerView.post(() ->
                recyclerView.smoothScrollToPosition(Math.max(0, messageList.size() - 1)));
    }

    @Override
    public void onResume() {
        super.onResume();
        // whenever we come back to this screen, clear unread count
        markThreadAsRead();
    }
}