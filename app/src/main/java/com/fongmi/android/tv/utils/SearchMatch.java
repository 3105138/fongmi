package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Vod;

import java.util.regex.Pattern;

public class SearchMatch {

    private static final Pattern SEASON = Pattern.compile("^(第[0-9一二三四五六七八九十百零〇]+季?|[0-9]+)$");

    public static boolean isExact(String keyword, Vod vod) {
        if (vod == null) return false;
        String key = normalize(keyword);
        String name = normalize(vod.getName());
        if (TextUtils.isEmpty(key) || TextUtils.isEmpty(name)) return false;
        if (name.equals(key)) return true;
        if (!name.startsWith(key)) return false;
        return SEASON.matcher(name.substring(key.length())).matches();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\s　]+", "").trim();
    }
}
