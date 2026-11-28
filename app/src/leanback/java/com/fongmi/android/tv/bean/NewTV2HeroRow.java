package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.utils.NewTV2HomeData;

/** First row on the NewTV2 TV home page. */
public final class NewTV2HeroRow {

    private NewTV2HomeData.HeroItem item;

    public NewTV2HomeData.HeroItem getItem() {
        return item;
    }

    public void setItem(NewTV2HomeData.HeroItem item) {
        this.item = item;
    }
}
