package com.sns.kanta;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.sns.kanta.databinding.ActivitySplashBinding;

public class SplashActivity extends AppCompatActivity {

    private static final long MIN_SPLASH_TIME = 1500;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivitySplashBinding binding;
    private boolean isConnected = false;
    private boolean hasMinTimePassed = false;
    private boolean isNetworkCheckComplete = false;
    private boolean isActivityFinishing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupRetryButton();
        startSplashSequence();
    }

    private void setupRetryButton() {
        binding.btnRetry.setOnClickListener(v -> {
            binding.errorLayout.setVisibility(View.GONE);
            binding.progressBar.setVisibility(View.VISIBLE);

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
            SharedPreferences prefs = getSharedPreferences("player_prefs", MODE_PRIVATE);
            boolean onboardingShown = prefs.getBoolean("onboarding_shown", false);

            Intent intent;
            if (!onboardingShown) {
                intent = new Intent(SplashActivity.this, OnboardingActivity.class);
            } else {
                intent = new Intent(SplashActivity.this, MainActivity.class);
            }

            startActivity(intent);
            isActivityFinishing = true;
            finish();
        } else if (!isActivityFinishing) {
            mainHandler.postDelayed(this::proceedToPlayerActivity, 100);
        }
    }

    private void showNoInternetError() {
        if (!isActivityFinishing) {
            binding.progressBar.setVisibility(View.GONE);
            binding.errorLayout.setVisibility(View.VISIBLE);
            Toast.makeText(this, R.string.error_check_connection, Toast.LENGTH_LONG).show();
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
