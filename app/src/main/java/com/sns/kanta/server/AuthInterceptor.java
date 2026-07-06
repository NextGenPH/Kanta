package com.sns.kanta.server;

import androidx.annotation.NonNull;

import com.sns.kanta.BuildConfig;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Enterprise interceptor for automatic header injection and security.
 */
public class AuthInterceptor implements Interceptor {

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        Request.Builder requestBuilder = original.newBuilder();

        if (original.header("apikey") == null) {
            requestBuilder.header("apikey", BuildConfig.SUPABASE_ANON_KEY);
        }
        if (original.header("Authorization") == null) {
            requestBuilder.header("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY);
        }

        return chain.proceed(requestBuilder.build());
    }
}
