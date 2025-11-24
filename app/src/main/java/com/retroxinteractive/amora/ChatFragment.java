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

import java.util.ArrayList;
import java.util.List;

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

    private String receiverId;
    private String receiverName;
    private String receiverPhotoUrl;
    private String roomId;

    public ChatFragment() {
        // Required empty public constructor
    }

    /**
     * Factory method you can use later when opening a specific chat
     * from a profile card / chat list.
     */
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

        if (receiverId != null && !receiverId.isEmpty()) {
            // Build a room id that is SAME for both sides (sorted uids)
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

            btnSend.setOnClickListener(v -> sendMessage());
        } else {
            // No receiver yet – disable send for now.
            btnSend.setOnClickListener(v ->
                    Toast.makeText(requireContext(),
                            "No chat selected yet", Toast.LENGTH_SHORT).show());
        }

        // Go back (will pop fragment / go back in activity stack)
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

        String title = (receiverName != null && !receiverName.isEmpty())
                ? receiverName
                : "Chat";
        txtName.setText(title);

        // You can later plug real online status here.
        txtStatus.setText("");

        if (receiverPhotoUrl != null && !receiverPhotoUrl.isEmpty()) {
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
        lm.setStackFromEnd(true); // start from bottom like chat apps
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
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(),
                                "Failed to send", Toast.LENGTH_SHORT).show());
    }

    private void listenForMessages() {
        if (chatRef == null) return;

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

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) { }
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) { }
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) { }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void scrollToBottom() {
        if (recyclerView == null || adapter == null) return;

        recyclerView.post(() ->
                recyclerView.smoothScrollToPosition(Math.max(0, messageList.size() - 1)));
    }
}