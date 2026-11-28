package com.fongmi.android.tv.utils;

/**
 * 刮削失败的<b>性质</b>分类，决定「要不要进黑名单」。
 *
 * <p>只有「TMDB 正常工作，但确实没有这部片」才算永久性失败：
 * 网络超时、请求被限流、鉴权失败、返回无法解析、图片下载失败这些都是临时性失败，
 * 过一会儿重试很可能就成功了，因此既不能拉黑，冷却时间也应缩短。</p>
 */
public final class ArtFailure {

    /** 本次没有失败（成功）。 */
    public static final int NONE = 0;
    /** 数据源正常返回，但确实没有匹配条目 —— 唯一会写入黑名单的类型。 */
    public static final int NO_MATCH = 1;
    /** 临时性失败：网络错误、超时、限流、鉴权、响应异常、素材下载失败等。 */
    public static final int TEMPORARY = 2;
    /** 刮削被关闭 / 未配置，本次没有真正请求。 */
    public static final int UNAVAILABLE = 3;
    /**
     * 数据源正常返回、条目也存在，但关键素材缺失（如候选均无可用图片）。
     * 这类<b>不能</b>据此认定「没有这部片」，因此不参与拉黑。
     */
    public static final int INCOMPLETE = 4;

    private ArtFailure() {
    }

    /** 是否属于「确认没有这部片」的永久性失败。 */
    public static boolean permanent(int type) {
        return type == NO_MATCH;
    }

    /** 是否属于可重试的软失败：无匹配与素材不全都属于。 */
    public static boolean fallback(int type) {
        return type == NO_MATCH || type == INCOMPLETE;
    }
}
