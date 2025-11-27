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
import com.google.firebase.database.ValueEventListener;

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

    private DatabaseReference chatsRoot;
    private DatabaseReference userChatsRoot;
    private DatabaseReference usersRoot;
    private DatabaseReference likesRoot;

    private String receiverId;
    private String receiverName;
    private String receiverPhotoUrl;

    private String roomId;
    private ChildEventListener messagesListener;

    private TextView tvMatched;

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
        chatsRoot = FirebaseDatabase.getInstance().getReference("chats");
        usersRoot = FirebaseDatabase.getInstance().getReference("users");
        likesRoot = FirebaseDatabase.getInstance().getReference("likes");

        if (currentUser != null && receiverId != null) {
            roomId = buildRoomId(currentUser.getUid(), receiverId);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_chat, container, false);
        initViews(root);
        setupRecycler();
        setupToolbar();
        setupBackButton();
        setupSendButton();
        observeMessages();
        markThreadAsRead();
        observeMatchedStatus();
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
        tvMatched = root.findViewById(R.id.tvMatched);
    }

    private void observeMatchedStatus() {
        if (currentUser == null || receiverId == null || tvMatched == null) {
            return;
        }

        String myId = currentUser.getUid();

        likesRoot.child(myId).child(receiverId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot myLikeSnap) {
                        if (!myLikeSnap.exists()) {
                            return;
                        }

                        likesRoot.child(receiverId).child(myId)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot otherLikeSnap) {
                                        if (otherLikeSnap.exists()) {
                                            String str = "MATCHED";
                                            tvMatched.setText(str);
                                        }
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        // ignore
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // ignore
                    }
                });
    }

    private void setupToolbar() {
        if (txtName == null || txtStatus == null) return;

        String title = !TextUtils.isEmpty(receiverName) ? receiverName : "Chat";
        txtName.setText(title);
        txtStatus.setText(""); // placeholder for status

        // Make header clickable to open the other user's profile
        View.OnClickListener openProfileClick = v -> openUserProfile();

        txtName.setOnClickListener(openProfileClick);
        if (imgProfile != null) {
            imgProfile.setOnClickListener(openProfileClick);
        }

        if (!TextUtils.isEmpty(receiverPhotoUrl)) {
            Glide.with(requireContext())
                    .load(receiverPhotoUrl)
                    .placeholder(R.drawable.ic_profile)
                    .into(imgProfile);
        } else {
            imgProfile.setImageResource(R.drawable.ic_profile);
        }
    }

    /**
     * Open the receiver's profile, similar to DiscoverFragment.openUserProfile().
     * This works even if ChatFragment is hosted by different activities/fragments.
     */
    private void openUserProfile() {
        if (TextUtils.isEmpty(receiverId)) {
            Toast.makeText(requireContext(), "User id missing", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create ProfileFragment for this user
        ProfileFragment fragment = ProfileFragment.newInstance(receiverId, null);

        // If we're inside MainActivity, also hide its bottom nav
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setBottomNavVisible(false);
        }

        // Use the container that currently hosts this ChatFragment
        int containerId = getId();
        if (containerId == View.NO_ID) {
            // Fallback to main container (used in MainActivity)
            containerId = R.id.main_fragment_container;
        }

        getParentFragmentManager()
                .beginTransaction()
                .replace(containerId, fragment)
                .addToBackStack("chat_to_profile")
                .commit();
    }

    private void setupRecycler() {
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);
        recyclerView.setLayoutManager(lm);
        adapter = new MessageAdapter(messageList);
        recyclerView.setAdapter(adapter);
    }

    private void setupBackButton() {
        btnBack.setOnClickListener(v -> {
            requireActivity().onBackPressed();
        });
    }

    private void setupSendButton() {
        btnSend.setOnClickListener(v -> {
            String text = edtMessage.getText().toString().trim();
            if (TextUtils.isEmpty(text)) {
                return;
            }
            sendMessage(text);
        });
    }

    private void observeMessages() {
        if (roomId == null) return;

        messageList.clear();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        DatabaseReference chatRef = chatsRoot.child(roomId);
        messagesListener = chatRef.orderByChild("timestamp")
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                        Message m = snapshot.getValue(Message.class);
                        if (m != null) {
                            messageList.add(m);
                            adapter.notifyItemInserted(messageList.size() - 1);
                            scrollToBottom();
                        }
                    }

                    @Override
                    public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                    }

                    @Override
                    public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                    }

                    @Override
                    public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }

    private void sendMessage(String text) {
        if (currentUser == null || roomId == null) return;

        DatabaseReference chatRef = chatsRoot.child(roomId);
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
                        Toast.makeText(requireContext(), "Failed to send", Toast.LENGTH_SHORT).show());
    }

    private void updateThreadsOnSend(String lastMessage, long timestamp) {
        if (currentUser == null || receiverId == null || roomId == null) return;

        String myId = currentUser.getUid();

        usersRoot.child(myId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                UserProfile me = snapshot.getValue(UserProfile.class);
                if (me == null) return;

                String myName = snapshot.child("name").getValue(String.class);
                String myPhoto = snapshot.child("profileImageUrl").getValue(String.class);

                if (myName == null) myName = "User";
                if (myPhoto == null) myPhoto = "";

                // 1) Sender side thread (me)
                ChatThread senderThread = new ChatThread(
                        roomId,
                        receiverId,
                        receiverName,
                        receiverPhotoUrl,
                        lastMessage,
                        timestamp,
                        0
                );

                userChatsRoot.child(myId)
                        .child(roomId)
                        .setValue(senderThread);

                // 2) Receiver side thread (other user)
                Map<String, Object> update = new HashMap<>();
                update.put("roomId", roomId);
                update.put("otherUserId", myId);
                update.put("otherUserName", myName);
                update.put("otherUserPhotoUrl", myPhoto);
                update.put("lastMessage", lastMessage);
                update.put("lastTimestamp", timestamp);
                update.put("unreadCount", ServerValue.increment(1));

                userChatsRoot.child(receiverId)
                        .child(roomId)
                        .updateChildren(update);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void markThreadAsRead() {
        if (currentUser == null || receiverId == null || roomId == null) return;

        userChatsRoot.child(currentUser.getUid())
                .child(roomId)
                .child("unreadCount")
                .setValue(0);
    }

    private String buildRoomId(String uid1, String uid2) {
        if (uid1.compareTo(uid2) < 0) {
            return uid1 + "_" + uid2;
        } else {
            return uid2 + "_" + uid1;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (messagesListener != null && roomId != null) {
            chatsRoot.child(roomId).removeEventListener(messagesListener);
            messagesListener = null;
        }
    }

    private void scrollToBottom() {
        recyclerView.post(() ->
                recyclerView.smoothScrollToPosition(Math.max(0, messageList.size() - 1)));
    }

    @Override
    public void onResume() {
        super.onResume();
        markThreadAsRead();
    }
}