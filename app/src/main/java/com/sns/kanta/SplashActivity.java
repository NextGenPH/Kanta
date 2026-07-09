package com.sns.kanta;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.sns.kanta.databinding.ActivitySplashBinding;

public class SplashActivity extends AppCompatActivity {

    private static final long MIN_SPLASH_TIME = 1000; // Snappy 1-second delay
    private static final int MAX_RETRIES = 3;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivitySplashBinding binding;

    private volatile boolean isConnected = false;
    private volatile boolean hasMinTimePassed = false;
    private volatile boolean isNetworkCheckComplete = false;
    private volatile boolean isProceedingStarted = false;
    private boolean isActivityFinishing = false;
    private final Runnable networkTimeoutRunnable = () -> {
        if (!isActivityFinishing) {
            isNetworkCheckComplete = true;
            isConnected = false;
            checkAndProceed();
        }
    };
    private int retryCount = 0;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        SharedPreferences prefs = getSharedPreferences("player_prefs", MODE_PRIVATE);
        int savedTheme = prefs.getInt("app_theme", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(savedTheme);

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this); // Modern full-bleed edge-to-edge

        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Apply window insets for edge-to-edge support
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());

            int topSafe = Math.max(insets.top, cutout.top);
            int bottomSafe = insets.bottom;
            int leftSafe = insets.left;
            int rightSafe = insets.right;

            binding.progressBar.setTranslationY(-bottomSafe);
            binding.errorLayout.setPadding(
                    leftSafe + (int) (16 * getResources().getDisplayMetrics().density),
                    topSafe,
                    rightSafe + (int) (16 * getResources().getDisplayMetrics().density),
                    bottomSafe
            );

            return WindowInsetsCompat.CONSUMED;
        });

        setupRetryButton();
        startSplashSequence();
    }

    private void setupRetryButton() {
        binding.btnRetry.setOnClickListener(v -> {
            if (retryCount >= MAX_RETRIES) {
                Toast.makeText(this, R.string.splash_max_retries_error, Toast.LENGTH_LONG).show();
                return;
            }
            retryCount++;

            binding.errorLayout.setVisibility(View.GONE);
            binding.progressBar.setVisibility(View.VISIBLE);

            isConnected = false;
            hasMinTimePassed = false;
            isNetworkCheckComplete = false;
            isProceedingStarted = false;

            // Clean up previous callback and timeout before retrying
            if (networkCallback != null) {
                ConnectivityManager connectivityManager = (ConnectivityManager)
                        getSystemService(CONNECTIVITY_SERVICE);
                try {
                    connectivityManager.unregisterNetworkCallback(networkCallback);
                } catch (Exception ignored) {
                }
                networkCallback = null;
            }
            mainHandler.removeCallbacks(networkTimeoutRunnable);

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

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) return false;
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void checkNetworkConnection() {
        if (isNetworkConnected()) {
            isConnected = true;
            isNetworkCheckComplete = true;
            checkAndProceed();
            return;
        }

        ConnectivityManager connectivityManager = (ConnectivityManager)
                getSystemService(CONNECTIVITY_SERVICE);

        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                mainHandler.post(() -> {
                    mainHandler.removeCallbacks(networkTimeoutRunnable);
                    if (!isNetworkCheckComplete && !isActivityFinishing) {
                        isNetworkCheckComplete = true;
                        isConnected = true;
                        checkAndProceed();
                    }
                });
            }
        };
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
        mainHandler.postDelayed(networkTimeoutRunnable, 3000);
    }

    private void checkAndProceed() {
        if (isProceedingStarted || isActivityFinishing) return;

        if (isNetworkCheckComplete && hasMinTimePassed) {
            if (isConnected) {
                isProceedingStarted = true;
                proceedToNextActivity();
            } else {
                showNoInternetError();
            }
        }
    }

    private void proceedToNextActivity() {
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
    }

    private void showNoInternetError() {
        binding.progressBar.setVisibility(View.GONE);
        binding.errorLayout.setVisibility(View.VISIBLE);
        Toast.makeText(this, R.string.error_check_connection, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityFinishing = true;
        mainHandler.removeCallbacksAndMessages(null);

        if (networkCallback != null) {
            ConnectivityManager connectivityManager = (ConnectivityManager)
                    getSystemService(CONNECTIVITY_SERVICE);
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
        }
    }
}
