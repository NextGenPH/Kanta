package com.sns.kanta.server;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.sns.kanta.BuildConfig;
import com.sns.kanta.model.ReportRequest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Response;

public final class ReportRepository {

    private static volatile ReportRepository instance;
    private final ApiService apiService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ReportRepository() {
        apiService = VideoRepository.getInstance().getApiService();
    }

    @NonNull
    public static ReportRepository getInstance() {
        if (instance == null) {
            synchronized (ReportRepository.class) {
                if (instance == null) {
                    instance = new ReportRepository();
                }
            }
        }
        return instance;
    }

    /**
     * Submits a report to Supabase database asynchronously.
     *
     * @param mediaId  The ID of the reported media.
     * @param reason   The selected report reason.
     * @param callback Callback to invoke on completion.
     */
    public void submitReport(@NonNull String mediaId, @NonNull String reason, @NonNull SubmitCallback callback) {
        executor.execute(() -> {
            try {
                ReportRequest request = new ReportRequest(mediaId, reason);
                Call<Void> call = apiService.submitReport(
                        BuildConfig.SUPABASE_ANON_KEY,
                        "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                        request
                );
                Response<Void> response = call.execute();

                if (response.isSuccessful()) {
                    mainHandler.post(callback::onSuccess);
                } else {
                    String errorMsg = "Server error " + response.code();
                    mainHandler.post(() -> callback.onFailure(new Exception(errorMsg)));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onFailure(e));
            }
        });
    }

    public interface SubmitCallback {
        void onSuccess();

        void onFailure(@NonNull Throwable t);
    }
}
