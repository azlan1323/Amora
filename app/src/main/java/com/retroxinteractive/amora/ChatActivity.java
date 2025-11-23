package com.retroxinteractive.amora;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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

public class ChatActivity extends AppCompatActivity {

    private ImageView btnBack, btnSend;
    private CircleImageView imgProfile;   // CircleImageView if you use that lib
    private TextView txtName, txtStatus;
    private EditText edtMessage;
    private RecyclerView recyclerView;

    private MessageAdapter adapter;
    private List<Message> messageList;

    private FirebaseUser currentUser;
    private DatabaseReference chatRef;

    private String receiverId;
    private String receiverName;
    private String receiverPhotoUrl;

    private String roomId;   // combined uid1_uid2 to keep chat unique

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            finish(); // user logged out
            return;
        }

        // Get data from intent
        receiverId = getIntent().getStringExtra("receiverId");
        receiverName = getIntent().getStringExtra("receiverName");
        receiverPhotoUrl = getIntent().getStringExtra("receiverPhotoUrl");

        if (receiverId == null) {
            finish();
            return;
        }

        init();
        setupToolbar();
        setupRecycler();

        // Build a room id that is SAME for both sides (sorted uids)
        String myId = currentUser.getUid();
        if (myId.compareTo(receiverId) < 0) {
            roomId = myId + "_" + receiverId;
        } else {
            roomId = receiverId + "_" + myId;
        }

        chatRef = FirebaseDatabase.getInstance().getReference("chats").child(roomId);

        listenForMessages();

        btnSend.setOnClickListener(v -> sendMessage());
        btnBack.setOnClickListener(v -> onBackPressed());
    }

    private void init() {
        btnBack = findViewById(R.id.btnBack);
        btnSend = findViewById(R.id.btnSend);
        imgProfile = findViewById(R.id.imgProfile);
        txtName = findViewById(R.id.txtName);
        txtStatus = findViewById(R.id.txtStatus);
        edtMessage = findViewById(R.id.edtMessage);
        recyclerView = findViewById(R.id.recyclerMessages);

        messageList = new ArrayList<>();
    }

    private void setupToolbar() {
        txtName.setText(receiverName != null ? receiverName : "User");
        txtStatus.setText(""); // you can later plug real online status

        if (receiverPhotoUrl != null && !receiverPhotoUrl.isEmpty()) {
            Glide.with(this)
                    .load(receiverPhotoUrl)
                    .placeholder(R.drawable.ic_nav_profile)
                    .into(imgProfile);
        }
    }

    private void setupRecycler() {
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true); // start from bottom like chat apps
        recyclerView.setLayoutManager(lm);

        adapter = new MessageAdapter(messageList);
        recyclerView.setAdapter(adapter);
    }

    private void sendMessage() {
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
                        Toast.makeText(ChatActivity.this,
                                "Failed to send", Toast.LENGTH_SHORT).show());
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

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, String s) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, String s) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void scrollToBottom() {
        recyclerView.post(() ->
                recyclerView.smoothScrollToPosition(Math.max(0, messageList.size() - 1)));
    }
}