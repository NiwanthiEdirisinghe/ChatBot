package com.example.chatbot;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;


import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.FormBody;
import okhttp3.RequestBody;
import okhttp3.Response;


public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";
    private static final String CHAT_API_URL = "http://10.0.2.2:5000/chat";

    private String username;
    private RecyclerView messagesRecyclerView;
    private EditText messageEditText;
    private ImageButton sendButton;
    private ImageButton backButton;
    private LinearLayout typingIndicator;

    private List<Message> messageList;
    private MessageAdapter messageAdapter;

    private OkHttpClient client;
    private ExecutorService executorService;
    private Handler mainHandler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        username = getIntent().getStringExtra("username");
        if (username == null || username.isEmpty()) {
            username = "User";
        }

        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messageEditText = findViewById(R.id.messageEditText);
        sendButton = findViewById(R.id.sendButton);
        backButton = findViewById(R.id.backButton);
        typingIndicator = findViewById(R.id.typingIndicator);

        messageList = new ArrayList<>();
        messageAdapter = new MessageAdapter(this, messageList, username);
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesRecyclerView.setAdapter(messageAdapter);

        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        addMessage(new Message("Welcome " + username + "!", false));

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String messageText = messageEditText.getText().toString().trim();
                if (messageText.isEmpty()) {
                    return;
                }

                addMessage(new Message(messageText, true));

                messageEditText.setText("");

                showTypingIndicator(true);

                sendMessageToChatbot(messageText);
            }
        });

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ChatActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            }
        });
    }

    private void addMessage(Message message) {
        messageList.add(message);
        messageAdapter.notifyItemInserted(messageList.size() - 1);
        messagesRecyclerView.smoothScrollToPosition(messageList.size() - 1);
    }

    private void showTypingIndicator(boolean show) {
        typingIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void sendMessageToChatbot(final String userMessage) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    RequestBody formBody = new FormBody.Builder()
                            .add("userMessage", userMessage)
                            .build();

                    Request request = new Request.Builder()
                            .url(CHAT_API_URL)
                            .post(formBody)
                            .build();

                    int typingDelay = 1000 + (int)(Math.random() * 2000);
                    Thread.sleep(typingDelay);

                    Response response = client.newCall(request).execute();
                    final String responseBody = response.body().string();

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    showTypingIndicator(false);

                                    if (response.isSuccessful()) {
                                        try {
                                            addMessage(new Message(responseBody, false));
                                        } catch (Exception e) {
                                            addMessage(new Message(responseBody, false));
                                        }
                                    } else {
                                        addMessage(new Message("Sorry, I couldn't process your request. Please try again.", false));
                                    }
                                }
                            }, 500);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error sending message to chatbot", e);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            showTypingIndicator(false);
                            addMessage(new Message("Network error. Please check your connection and try again.", false));
                        }
                    });
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}