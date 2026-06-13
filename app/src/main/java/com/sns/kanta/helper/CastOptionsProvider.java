package com.sns.kanta.helper;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;

import java.util.List;

public final class CastOptionsProvider implements OptionsProvider {
    @NonNull
    @Override
    public CastOptions getCastOptions(@NonNull Context context) {
        return new CastOptions.Builder()
                .setReceiverApplicationId("CC1AD845") // Default YouTube Cast receiver
                .build();
    }

    @Override
    public List<SessionProvider> getAdditionalSessionProviders(@NonNull Context context) {
        return null;
    }
}
