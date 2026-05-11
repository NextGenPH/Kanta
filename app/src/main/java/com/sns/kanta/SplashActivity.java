package com.sns.kanta;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class SplashActivity extends AppCompatActivity {

    private static final long MIN_SPLASH_TIME = 1500;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ProgressBar progressBar;
    private LinearLayout errorLayout;
    private MaterialButton btnRetry;
    private boolean isConnected = false;
    private boolean hasMinTimePassed = false;
    private boolean isNetworkCheckComplete = false;
    private boolean isActivityFinishing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        initViews();
        setupRetryButton();
        startSplashSequence();
    }

    private void initViews() {
        progressBar = findViewById(R.id.progressBar);
        errorLayout = findViewById(R.id.errorLayout);
        btnRetry = findViewById(R.id.btnRetry);
    }

    private void setupRetryButton() {
        btnRetry.setOnClickListener(v -> {
            errorLayout.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);

            isConnected = false;
            hasMinTimePassed = false;
            isNetworkCheckComplete = false;

            startSplashSequence();
        });
    }

    private void startSplashSequence() {
        mainHandler.postDelayed(() -> {
            hasMinTimePassed = true;
            checkAndProceed();
        }, MIN_SPLASH_TIME);

        checkNetworkConnection();
    }

    private void checkNetworkConnection() {
        ConnectivityManager connectivityManager = (ConnectivityManager)
                getSystemService(CONNECTIVITY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            connectivityManager.registerNetworkCallback(networkRequest, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    if (!isNetworkCheckComplete && !isActivityFinishing) {
                        isNetworkCheckComplete = true;
                        isConnected = true;
                        handleNetworkResult();
                    }
                }

                @Override
                public void onUnavailable() {
                    if (!isNetworkCheckComplete && !isActivityFinishing) {
                        isNetworkCheckComplete = true;
                        isConnected = false;
                        handleNetworkResult();
                    }
                }
            });
        } else {
            android.net.NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            isNetworkCheckComplete = true;
            handleNetworkResult();
        }
    }

    private void handleNetworkResult() {
        if (isConnected) {
            proceedToPlayerActivity();
        } else {
            showNoInternetError();
        }
    }

    private void proceedToPlayerActivity() {
        if (!isActivityFinishing && hasMinTimePassed && isConnected) {
            Intent intent = new Intent(SplashActivity.this, Mainactivity.class);
            startActivity(intent);
            isActivityFinishing = true;
            finish();
        } else if (!isActivityFinishing) {
            mainHandler.postDelayed(this::proceedToPlayerActivity, 100);
        }
    }

    private void showNoInternetError() {
        if (!isActivityFinishing) {
            progressBar.setVisibility(View.GONE);
            errorLayout.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Please check your internet connection", Toast.LENGTH_LONG).show();
        }
    }

    private void checkAndProceed() {
        if (isNetworkCheckComplete && hasMinTimePassed && isConnected && !isActivityFinishing) {
            proceedToPlayerActivity();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityFinishing = true;
        mainHandler.removeCallbacksAndMessages(null);
    }
}