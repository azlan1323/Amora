package com.retroxinteractive.amora;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ChatListFragment extends Fragment {

    private RecyclerView recyclerChats;
    private ChatThreadAdapter adapter;
    private List<ChatThread> threads = new ArrayList<>();

    private FirebaseUser currentUser;
    private DatabaseReference userChatsRef;

    public ChatListFragment() {
        // Required empty constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_chat_list, container, false);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show();
            return root;
        }

        recyclerChats = root.findViewById(R.id.recyclerChats);
        recyclerChats.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ChatThreadAdapter(threads, thread -> {
            // open actual chat
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openChatWith(
                        thread.otherUserId,
                        thread.otherUserName,
                        thread.otherUserPhotoUrl
                );
            }
        });
        recyclerChats.setAdapter(adapter);

        userChatsRef = FirebaseDatabase.getInstance()
                .getReference("userChats")
                .child(currentUser.getUid());

        listenForChatThreads();

        return root;
    }

    private void listenForChatThreads() {
        userChatsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                threads.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    ChatThread t = child.getValue(ChatThread.class);
                    if (t != null) {
                        threads.add(t);
                    }
                }

                // Sort by lastTimestamp (descending: newest on top)
                Collections.sort(threads, new Comparator<ChatThread>() {
                    @Override
                    public int compare(ChatThread o1, ChatThread o2) {
                        return Long.compare(o2.lastTimestamp, o1.lastTimestamp);
                    }
                });

                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(requireContext(),
                        "Failed to load chats", Toast.LENGTH_SHORT).show();
            }
        });
    }
}