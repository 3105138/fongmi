package com.fongmi.android.tv.ui.presenter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.Presenter;

import java.util.function.IntConsumer;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.NewTV2HeroRow;
import com.fongmi.android.tv.utils.NewTV2HomeData;

/** The real first RecyclerView row: it reserves the hero slot and draws its content above the backdrop. */
public final class NewTV2HeroPresenter extends Presenter {

    public interface OnClickListener {
        void onClick(NewTV2HomeData.HeroItem item);
    }

    private static final int HERO_HEIGHT_DP = 440;
    private final OnClickListener listener;
    private final IntConsumer navigation;

    public NewTV2HeroPresenter(OnClickListener listener, IntConsumer navigation) {
        this.listener = listener;
        this.navigation = navigation;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        Context context = parent.getContext();
        FrameLayout root = new FrameLayout(context);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, HERO_HEIGHT_DP)));
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setClipChildren(false);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.setId(R.id.newtv2HeroRow);
        root.setNextFocusUpId(R.id.newtv2NavHome);
        root.setClickable(true);

        LinearLayout details = new LinearLayout(context);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(context, 48), 0, dp(context, 48), dp(context, 28));
        details.setClipChildren(false);
        FrameLayout.LayoutParams detailsParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        root.addView(details, detailsParams);

        TextView label = new TextView(context);
        label.setText(R.string.newtv2_hero_label);
        label.setTextColor(0xFFE58A45);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setLetterSpacing(0.08f);
        details.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView logo = new ImageView(context);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setAdjustViewBounds(true);
        logo.setMaxWidth(dp(context, 420));
        logo.setMaxHeight(dp(context, 100));
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 96));
        logoParams.topMargin = dp(context, 8);
        details.addView(logo, logoParams);
        logo.setVisibility(View.GONE);

        TextView title = new TextView(context);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 38);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        title.setMaxLines(2);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(context, 8);
        details.addView(title, titleParams);

        TextView meta = new TextView(context);
        meta.setTextColor(0xCCFFFFFF);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        meta.setMaxLines(1);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(context, 8);
        details.addView(meta, metaParams);

        TextView overview = new TextView(context);
        overview.setTextColor(0xD9FFFFFF);
        overview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        overview.setMaxLines(2);
        overview.setEllipsize(TextUtils.TruncateAt.END);
        overview.setLineSpacing(dp(context, 2), 1.15f);
        LinearLayout.LayoutParams overviewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        overviewParams.topMargin = dp(context, 6);
        details.addView(overview, overviewParams);

        TextView action = new TextView(context);
        action.setText(R.string.newtv2_hero_action);
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        action.setFocusable(false);
        action.setClickable(false);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 40));
        actionParams.topMargin = dp(context, 14);
        details.addView(action, actionParams);

        HeroHolder holder = new HeroHolder(root, logo, title, meta, overview, action);
        root.setOnFocusChangeListener((view, focused) -> styleAction(holder, focused));
        root.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN || navigation == null) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                navigation.accept(-1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                navigation.accept(1);
                return true;
            }
            return false;
        });
        root.setOnClickListener(view -> {
            if (holder.item != null && listener != null) listener.onClick(holder.item);
        });
        styleAction(holder, false);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, Object item) {
        HeroHolder holder = (HeroHolder) viewHolder;
        NewTV2HomeData.HeroItem data = ((NewTV2HeroRow) item).getItem();
        holder.item = data;
        Glide.with(holder.logo).clear(holder.logo);
        holder.logo.setVisibility(View.GONE);
        holder.title.setVisibility(View.VISIBLE);
        holder.meta.setVisibility(View.GONE);
        holder.overview.setVisibility(View.GONE);
        holder.action.setVisibility(data == null ? View.GONE : View.VISIBLE);

        if (data == null) {
            holder.title.setText("");
            return;
        }

        holder.title.setText(data.getTitle());
        StringBuilder meta = new StringBuilder();
        if (!TextUtils.isEmpty(data.getRating())) meta.append("TMDB ").append(data.getRating());
        if (!TextUtils.isEmpty(data.getYear())) {
            if (meta.length() > 0) meta.append("  ·  ");
            meta.append(data.getYear());
        }
        if (!TextUtils.isEmpty(data.getGenre())) {
            if (meta.length() > 0) meta.append("  ·  ");
            meta.append(data.getGenre());
        }
        if (meta.length() > 0) {
            holder.meta.setText(meta.toString());
            holder.meta.setVisibility(View.VISIBLE);
        }
        if (!TextUtils.isEmpty(data.getDescription())) {
            holder.overview.setText(data.getDescription());
            holder.overview.setVisibility(View.VISIBLE);
        }

        if (!TextUtils.isEmpty(data.getLogo())) {
            String logo = NewTV2HomeData.proxyImage(data.getLogo(), "original");
            String fallback = NewTV2HomeData.officialImage(data.getLogo(), "original");
            Glide.with(holder.logo).load(logo).error(Glide.with(holder.logo).load(fallback))
                    .fitCenter().listener(new RequestListener<Drawable>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                            @NonNull Target<Drawable> target, boolean first) {
                    holder.logo.setVisibility(View.GONE);
                    holder.title.setVisibility(View.VISIBLE);
                    return false;
                }

                @Override
                public boolean onResourceReady(@NonNull Drawable resource, Object model,
                                               @NonNull Target<Drawable> target,
                                               @Nullable com.bumptech.glide.load.DataSource source,
                                               boolean first) {
                    holder.title.setVisibility(View.GONE);
                    holder.logo.setVisibility(View.VISIBLE);
                    return false;
                }
            }).into(holder.logo);
        }
    }

    @Override
    public void onUnbindViewHolder(@NonNull ViewHolder viewHolder) {
        HeroHolder holder = (HeroHolder) viewHolder;
        holder.item = null;
        Glide.with(holder.logo).clear(holder.logo);
    }

    private static void styleAction(HeroHolder holder, boolean focused) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(focused ? Color.WHITE : 0x993C3C3C);
        background.setCornerRadius(dp(holder.view.getContext(), 6));
        holder.action.setBackground(background);
        holder.action.setTextColor(focused ? 0xFF202124 : Color.WHITE);
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class HeroHolder extends ViewHolder {
        final FrameLayout view;
        final ImageView logo;
        final TextView title;
        final TextView meta;
        final TextView overview;
        final TextView action;
        NewTV2HomeData.HeroItem item;

        HeroHolder(FrameLayout view, ImageView logo, TextView title, TextView meta,
                   TextView overview, TextView action) {
            super(view);
            this.view = view;
            this.logo = logo;
            this.title = title;
            this.meta = meta;
            this.overview = overview;
            this.action = action;
        }
    }
}
