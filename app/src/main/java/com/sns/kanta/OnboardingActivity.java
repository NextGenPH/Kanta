package com.sns.kanta;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayoutMediator;
import com.sns.kanta.databinding.ActivityOnboardingBinding;

import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private static final String PREF_NAME = "player_prefs";
    private static final String PREF_ONBOARDING_SHOWN = "onboarding_shown";
    private ActivityOnboardingBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupWindowInsets();

        List<OnboardingItem> items = new ArrayList<>();
        items.add(new OnboardingItem(
                R.drawable.app_ic,
                getString(R.string.onboarding_title_1),
                getString(R.string.onboarding_desc_1)
        ));
        items.add(new OnboardingItem(
                R.drawable.ic_search,
                getString(R.string.onboarding_title_2),
                getString(R.string.onboarding_desc_2)
        ));
        items.add(new OnboardingItem(
                R.drawable.ic_queue_music,
                getString(R.string.onboarding_title_3),
                getString(R.string.onboarding_desc_3)
        ));

        OnboardingAdapter adapter = new OnboardingAdapter(items);
        binding.viewPager.setAdapter(adapter);
        binding.viewPager.setOffscreenPageLimit(1);

        // Custom Page Transformer for smooth transitions
        binding.viewPager.setPageTransformer((page, position) -> {
            float absPos = Math.abs(position);
            page.setAlpha(1 - absPos);

            View iv = page.findViewById(R.id.ivOnboarding);
            View title = page.findViewById(R.id.tvTitle);
            View desc = page.findViewById(R.id.tvDescription);

            if (iv != null) iv.setTranslationX(position * -200);
            if (title != null) title.setTranslationX(position * 300);
            if (desc != null) desc.setTranslationX(position * 500);
        });

        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> {
        }).attach();

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == items.size() - 1) {
                    binding.btnNext.setText(R.string.btn_get_started);
                    binding.btnSkip.animate().alpha(0f).setDuration(200).withEndAction(() -> binding.btnSkip.setVisibility(View.GONE)).start();
                } else {
                    binding.btnNext.setText(R.string.btn_next);
                    if (binding.btnSkip.getVisibility() != View.VISIBLE) {
                        binding.btnSkip.setVisibility(View.VISIBLE);
                        binding.btnSkip.setAlpha(0f);
                        binding.btnSkip.animate().alpha(1f).setDuration(200).start();
                    }
                }
            }
        });

        binding.btnNext.setOnClickListener(v -> {
            if (binding.viewPager.getCurrentItem() < items.size() - 1) {
                binding.viewPager.setCurrentItem(binding.viewPager.getCurrentItem() + 1);
            } else {
                completeOnboarding();
            }
        });

        binding.btnSkip.setOnClickListener(v -> completeOnboarding());
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets displayCutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());

            int topSafe = Math.max(systemBars.top, displayCutout.top);
            int bottomSafe = systemBars.bottom;
            int leftSafe = systemBars.left;
            int rightSafe = systemBars.right;

            binding.coordinatorLayout.setPadding(leftSafe, 0, rightSafe, 0);

            // Adjust Skip button for status bar
            ViewGroup.MarginLayoutParams skipLp = (ViewGroup.MarginLayoutParams) binding.btnSkip.getLayoutParams();
            skipLp.topMargin = (int) (16 * getResources().getDisplayMetrics().density) + topSafe;
            binding.btnSkip.setLayoutParams(skipLp);

            // Adjust Bottom Controls for nav bar
            android.view.View bottomControls = findViewById(R.id.bottomControls);
            if (bottomControls != null) {
                bottomControls.setPadding(
                        bottomControls.getPaddingLeft(),
                        bottomControls.getPaddingTop(),
                        bottomControls.getPaddingRight(),
                        bottomSafe
                );
            }

            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void completeOnboarding() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_ONBOARDING_SHOWN, true).apply();

        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private static class OnboardingItem {
        int imageRes;
        String title;
        String description;

        OnboardingItem(int imageRes, String title, String description) {
            this.imageRes = imageRes;
            this.title = title;
            this.description = description;
        }
    }

    private static class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.ViewHolder> {
        private final List<OnboardingItem> items;

        OnboardingAdapter(List<OnboardingItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_onboarding, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            OnboardingItem item = items.get(position);
            holder.ivImage.setImageResource(item.imageRes);
            holder.tvTitle.setText(item.title);
            holder.tvDescription.setText(item.description);

            // Initial Entrance Animation
            holder.ivImage.setTranslationY(100f);
            holder.ivImage.setAlpha(0f);
            holder.tvTitle.setTranslationY(50f);
            holder.tvTitle.setAlpha(0f);
            holder.tvDescription.setTranslationY(30f);
            holder.tvDescription.setAlpha(0f);

            holder.ivImage.animate().translationY(0f).alpha(1f).setDuration(600).setStartDelay(100).setInterpolator(new AccelerateDecelerateInterpolator()).start();
            holder.tvTitle.animate().translationY(0f).alpha(1f).setDuration(600).setStartDelay(250).setInterpolator(new AccelerateDecelerateInterpolator()).start();
            holder.tvDescription.animate().translationY(0f).alpha(1f).setDuration(600).setStartDelay(400).setInterpolator(new AccelerateDecelerateInterpolator()).start();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivImage;
            TextView tvTitle;
            TextView tvDescription;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivImage = itemView.findViewById(R.id.ivOnboarding);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvDescription = itemView.findViewById(R.id.tvDescription);
            }
        }
    }
}