package com.fongmi.android.tv.setting;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.TmdbApi;
import com.github.catvod.utils.Prefers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TMDB 刮削配置：全局连接方式 + 自定义反代地址 + 独立 API 密钥。
 *
 * <p>连接方式决定所有 TMDB 请求的基地址：默认反代、官方 API、自定义反代。
 * 项目内不再有其它数据源，所有图片与元数据拉取都经由这里给出的地址。</p>
 *
 * <p><b>密钥与地址分离</b>：API 密钥单独存于 {@code tmdb_key}，三种连接方式下都参与请求，
 * 不依赖是否填写自定义反代地址；切换连接方式不会清空或失效。站点记录
 * {@code {"url":"...","key":"..."}} 中的 {@code key} 仅作为兼容副本：
 * 保存地址时会同步独立密钥，旧版本回滚后仍能读到同一份密钥。</p>
 *
 * <p>兼容早期版本：旧版 {@code tmdb_api_url} + {@code tmdb_api_key} 或站点数组会在首次读取时
 * 迁移成单条站点记录；密钥在首次读取时从站点记录 / 更早版本键迁移到独立键，
 * 迁移后立即落盘，不会因切换连接方式而丢失。</p>
 */
public final class TmdbSetting {

    /** 默认反代：内置公共镜像，密钥可留空。 */
    public static final int MODE_PROXY = 0;
    /** 官方 API：使用 themoviedb 官方接口地址。 */
    public static final int MODE_OFFICIAL = 1;
    /** 自定义反代：地址与密钥由用户提供。 */
    public static final int MODE_CUSTOM = 2;

    public static final String PROXY_API_URL = "https://t.tmdbapi.cc.cd";
    public static final String OFFICIAL_API_URL = "https://api.themoviedb.org";

    private static final String KEY_MODE = "tmdb_mode";
    private static final String KEY_SITE = "tmdb_site";
    /**
     * 独立 API 密钥：与连接方式、自定义地址完全解耦。
     *
     * <p>键一旦写入（包括写入空串）就以它为准，不再回落到旧配置 —— 这样用户主动清空密钥
     * 不会被迁移逻辑用旧值“复活”。</p>
     */
    private static final String KEY_API_KEY = "tmdb_key";
    private static final String KEY_LEGACY_SITES = "tmdb_sites";
    private static final String KEY_LEGACY_URL = "tmdb_api_url";
    private static final String KEY_LEGACY_KEY = "tmdb_api_key";

    /* ------------------------------------------------------------ 图片分辨率 */

    /** 海报（竖版）：默认取列表最高档。 */
    public static final String[] POSTER_SIZES = {"w342", "w500", "w780"};
    /** 背景图（横版剧照）：默认取列表最高档。 */
    public static final String[] BACKDROP_SIZES = {"w780", "w1280"};
    /** 单集剧照：暂未接线，仅保存选择。 */
    public static final String[] STILL_SIZES = {"w185", "w300"};
    /** 人物头像：暂未接线，仅保存选择。 */
    public static final String[] PROFILE_SIZES = {"w185", "h632"};

    /** 各类尺寸的默认下标：海报 / 背景图取最高档，剧照 / 头像取 w185。 */
    private static final int POSTER_DEFAULT = POSTER_SIZES.length - 1;
    private static final int BACKDROP_DEFAULT = BACKDROP_SIZES.length - 1;
    private static final int STILL_DEFAULT = 0;
    private static final int PROFILE_DEFAULT = 0;

    private static final String KEY_IMAGE_POSTER = "tmdb_image_poster";
    private static final String KEY_IMAGE_BACKDROP = "tmdb_image_backdrop";
    private static final String KEY_IMAGE_STILL = "tmdb_image_still";
    private static final String KEY_IMAGE_PROFILE = "tmdb_image_profile";

    /** 轮换游标保留给 API 层的统一请求入口；当前只有一个全局站点。 */
    private static final AtomicInteger CURSOR = new AtomicInteger();
    private static volatile TmdbSite cache;
    /** 独立密钥的内存副本：每次 TMDB 请求都会读它，避免反复走偏好存储。 */
    private static volatile String cacheKey;

    private TmdbSetting() {
    }

    /** 当前连接方式。 */
    public static int getMode() {
        int mode = Prefers.getInt(KEY_MODE, MODE_PROXY);
        return mode == MODE_OFFICIAL || mode == MODE_CUSTOM ? mode : MODE_PROXY;
    }

    public static void putMode(int mode) {
        if (mode != MODE_PROXY && mode != MODE_OFFICIAL && mode != MODE_CUSTOM) mode = MODE_PROXY;
        Prefers.put(KEY_MODE, mode);
        CURSOR.set(0);
        TmdbApi.onConfigChanged();
    }

    /** 用户保存的站点记录；固定连接方式下地址字段不参与请求，但密钥仍从这里读。 */
    public static TmdbSite getSite() {
        TmdbSite site = cache;
        if (site != null) return site;
        synchronized (TmdbSetting.class) {
            if (cache != null) return cache;
            cache = migrate();
            return cache;
        }
    }

    public static void putSite(TmdbSite site) {
        TmdbSite value = site == null ? new TmdbSite() : site;
        /*
         * 密钥副本双写：独立键是唯一读取来源，站点记录里的 key 只用于兼容旧版本读取路径。
         * 保存地址时若带了密钥就同步到独立键；没带（新流程下地址弹窗不再编辑密钥）
         * 就把独立键的当前值补进记录，避免两份数据不一致。
         */
        if (!TextUtils.isEmpty(value.getKey())) {
            Prefers.put(KEY_API_KEY, value.getKey());
            cacheKey = value.getKey();
        } else {
            value.setKey(getApiKey());
        }
        Prefers.put(KEY_SITE, App.gson().toJson(value));
        cache = value;
        CURSOR.set(0);
        TmdbApi.onConfigChanged();
    }

    /**
     * 统一站点列表：API 层沿用它做请求入口，内容永远只有当前连接方式对应的那一条。
     */
    public static List<TmdbSite> getSites() {
        // 密钥来自独立配置：默认反代 / 官方直连 / 自定义反代三种方式下都生效。
        TmdbSite active = new TmdbSite(getApiBase(), getApiKey());
        return Collections.singletonList(active);
    }

    /** 当前连接方式实际使用的 API 基地址，全局唯一来源。 */
    public static String getApiBase() {
        if (getMode() == MODE_OFFICIAL) return OFFICIAL_API_URL;
        if (getMode() == MODE_CUSTOM) return getSite().base();
        return PROXY_API_URL;
    }

    /**
     * 当前 API 密钥；三种连接方式共用，与是否填写自定义地址无关。
     *
     * <p>首次读取时做一次性迁移：旧配置的密钥可能在站点记录里，也可能在更早版本的
     * {@code tmdb_api_key} 键里。取到后立即写入独立键，后续切换连接方式、修改地址都不受影响。</p>
     *
     * <p>用「键是否存在」而不是「值是否为空」判断是否已迁移：用户主动清空密钥会写入空串，
     * 此时必须尊重清空结果，不能再回落到旧值。</p>
     */
    public static String getApiKey() {
        String cached = cacheKey;
        if (cached != null) return cached;
        if (Prefers.getPrefers().contains(KEY_API_KEY)) {
            String saved = Prefers.getString(KEY_API_KEY, "");
            cacheKey = saved == null ? "" : saved;
            return cacheKey;
        }
        String legacy = getSite().getKey();
        if (TextUtils.isEmpty(legacy)) legacy = Prefers.getString(KEY_LEGACY_KEY, "");
        legacy = legacy == null ? "" : legacy.trim();
        Prefers.put(KEY_API_KEY, legacy);
        cacheKey = legacy;
        return legacy;
    }

    /**
     * 保存 API 密钥（允许传空串表示清除）。
     *
     * <p>只写独立键，不改连接方式与自定义地址；站点记录里的副本同步更新，
     * 保证旧版本回滚后读到的仍是同一份密钥。</p>
     */
    public static void putApiKey(String key) {
        String value = key == null ? "" : key.trim();
        Prefers.put(KEY_API_KEY, value);
        cacheKey = value;
        TmdbSite site = getSite();
        if (!value.equals(site.getKey())) {
            site.setKey(value);
            Prefers.put(KEY_SITE, App.gson().toJson(site));
            cache = site;
        }
        CURSOR.set(0);
        TmdbApi.onConfigChanged();
    }

    /** 设置页摘要：只露头尾，避免整串密钥铺在电视屏幕上；未设置返回空串。 */
    public static String getApiKeyMasked() {
        return TmdbSite.mask(getApiKey());
    }

    /**
     * 海报分辨率：新抓取的海报按这个档位下载。
     *
     * <p>已落库的素材保留原尺寸（索引里存的是具体文件名），只有之后新刮削的影片才换档，
     * 避免改一次设置就触发全库重抓。</p>
     */
    public static String getPosterSize() {
        return readSize(KEY_IMAGE_POSTER, POSTER_SIZES, POSTER_DEFAULT);
    }

    public static void putPosterSize(String size) {
        writeSize(KEY_IMAGE_POSTER, size, POSTER_SIZES, POSTER_DEFAULT);
    }

    /** 横版剧照（背景图）分辨率：新抓取按这个档位下载。 */
    public static String getBackdropSize() {
        return readSize(KEY_IMAGE_BACKDROP, BACKDROP_SIZES, BACKDROP_DEFAULT);
    }

    public static void putBackdropSize(String size) {
        writeSize(KEY_IMAGE_BACKDROP, size, BACKDROP_SIZES, BACKDROP_DEFAULT);
    }

    /** 单集剧照分辨率：暂未接线，仅保存用户选择。 */
    public static String getStillSize() {
        return readSize(KEY_IMAGE_STILL, STILL_SIZES, STILL_DEFAULT);
    }

    public static void putStillSize(String size) {
        writeSize(KEY_IMAGE_STILL, size, STILL_SIZES, STILL_DEFAULT);
    }

    /** 人物头像分辨率：暂未接线，仅保存用户选择。 */
    public static String getProfileSize() {
        return readSize(KEY_IMAGE_PROFILE, PROFILE_SIZES, PROFILE_DEFAULT);
    }

    public static void putProfileSize(String size) {
        writeSize(KEY_IMAGE_PROFILE, size, PROFILE_SIZES, PROFILE_DEFAULT);
    }

    /**
     * 尺寸在候选列表里的位置；非法值（含首次安装的空串）回落到默认档位。
     * 设置页弹窗用它定位当前选中项（{@code highest} = 默认取最高档）。
     */
    public static int indexOf(String[] sizes, String size, boolean highest) {
        for (int i = 0; i < sizes.length; i++) if (sizes[i].equals(size)) return i;
        return highest ? sizes.length - 1 : 0;
    }

    private static String readSize(String key, String[] sizes, int defIndex) {
        String value = Prefers.getString(key, "");
        for (String size : sizes) if (size.equals(value)) return size;
        return sizes[defIndex];
    }

    private static void writeSize(String key, String value, String[] sizes, int defIndex) {
        String next = sizes[defIndex];
        for (String size : sizes) if (size.equals(value)) next = size;
        Prefers.put(key, next);
    }

    /** 是否已有可用地址；自定义反代必须由用户填地址。 */
    public static boolean isConfigured() {
        String base = getApiBase();
        if (TextUtils.isEmpty(base)) return false;
        if (getMode() != MODE_CUSTOM) return true;
        return getSite().isValid();
    }

    /** 备份恢复 / 偏好整体替换后调用，重新解析当前站点与独立密钥。 */
    public static void reset() {
        cache = null;
        cacheKey = null;
        CURSOR.set(0);
    }

    /**
     * 连接方式对应的文案资源。
     *
     * <p>设置页摘要与选择弹窗共用同一份映射，避免两处顺序/文案不一致。</p>
     */
    public static int getModeLabelRes() {
        int mode = getMode();
        if (mode == MODE_OFFICIAL) return R.string.tmdb_mode_official;
        if (mode == MODE_CUSTOM) return R.string.tmdb_mode_custom;
        return R.string.tmdb_mode_proxy;
    }

    public static int nextSiteIndex(int size) {
        if (size <= 1) return 0;
        return (CURSOR.getAndIncrement() & 0x7FFFFFFF) % size;
    }

    /**
     * 是否参与刮削。
     *
     * <p>「启用刮削」总开关已移除：TMDB 是唯一数据源，只要连接方式给出可用地址就一直启用，
     * 没有地址（自定义反代未填）时自然停用。保留这个方法名给调用方复用。</p>
     */
    public static boolean isEnabled() {
        return isConfigured();
    }

    private static TmdbSite migrate() {
        String json = Prefers.getString(KEY_SITE, "");
        if (!TextUtils.isEmpty(json)) {
            try {
                TmdbSite site = App.gson().fromJson(json, TmdbSite.class);
                if (site != null) return site;
            } catch (Exception ignored) {
                // 配置损坏时继续尝试更早的版本。
            }
        }
        String sites = Prefers.getString(KEY_LEGACY_SITES, "");
        if (!TextUtils.isEmpty(sites)) {
            try {
                TmdbSite[] array = App.gson().fromJson(sites, TmdbSite[].class);
                if (array != null) for (TmdbSite site : array) if (site != null && site.isValid()) return site;
            } catch (Exception ignored) {
                // 继续尝试旧的单站点配置。
            }
        }
        String url = Prefers.getString(KEY_LEGACY_URL, "");
        String key = Prefers.getString(KEY_LEGACY_KEY, "");
        return new TmdbSite(url == null ? "" : url.trim(), key == null ? "" : key.trim());
    }
}
