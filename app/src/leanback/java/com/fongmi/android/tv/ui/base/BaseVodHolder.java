package com.fongmi.android.tv.ui.base;

import android.view.View;

import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.custom.PosterFocusAnimator;

public abstract class BaseVodHolder extends Presenter.ViewHolder {

    private final View card;

    public BaseVodHolder(View view) {
        super(view);
        this.card = view;
        if (Setting.isNewTV2Theme()) PosterFocusAnimator.bind(view, false);
    }

    protected void resetFocus() {
        if (Setting.isNewTV2Theme()) PosterFocusAnimator.reset(card);
    }

    public abstract void initView(Vod item);

    public abstract void unbind();
}
