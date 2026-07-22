package com.xa.pixiv;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

import com.google.android.material.button.MaterialButton;
import com.xa.pixiv.data.PixivAuthClient;
import com.xa.pixiv.data.SessionStore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LoginActivity extends AppCompatActivity {
    private PixivAuthClient auth;
    private SessionStore session;
    private ExecutorService executor;
    private MaterialButton loginButton;
    private MaterialButton demoButton;
    private ProgressBar progress;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        auth = new PixivAuthClient(this);
        session = new SessionStore(this);
        executor = Executors.newSingleThreadExecutor();
        loginButton = findViewById(R.id.pixiv_login_button);
        demoButton = findViewById(R.id.demo_button);
        progress = findViewById(R.id.login_progress);
        status = findViewById(R.id.login_status);
        loginButton.setOnClickListener(v -> launchLogin());
        demoButton.setOnClickListener(v -> {
            session.setOnboarded();
            openMain();
        });
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void launchLogin() {
        Uri url = Uri.parse(auth.startLoginUrl());
        try {
            CustomTabsIntent tabs = new CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build();
            tabs.launchUrl(this, url);
        } catch (Exception ignored) {
            startActivity(new Intent(Intent.ACTION_VIEW, url));
        }
    }

    private void handleIntent(Intent intent) {
        Uri uri = intent == null ? null : intent.getData();
        if (!auth.isCallback(uri)) return;
        setBusy(true, "正在交换登录凭证…");
        executor.execute(() -> {
            PixivAuthClient.AuthResult result = auth.handleCallback(uri);
            runOnUiThread(() -> {
                if (result.success) {
                    session.saveAuth(result);
                    status.setText("登录成功，正在载入你的推荐星图");
                    openMain();
                } else {
                    setBusy(false, result.error);
                }
            });
        });
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!busy);
        demoButton.setEnabled(!busy);
        status.setText(message);
    }

    private void openMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override protected void onDestroy() {
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }
}
