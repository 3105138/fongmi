package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import com.google.android.material.color.MaterialColors;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.custom.CustomWallView;
import com.fongmi.android.tv.utils.Notify;
import com.jeffmony.downloader.VideoDownloadManager;
import com.jeffmony.downloader.listener.DownloadListener;
import com.jeffmony.downloader.listener.IDownloadInfosCallback;
import com.jeffmony.downloader.model.VideoTaskItem;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class DownloadActivity extends AppCompatActivity {

    private int surfaceColor;
    private int cardColor;
    private int primaryColor;
    private int textColor;
    private int mutedColor;
    private int buttonColor;
    private int buttonTextColor;

    private final List<VideoTaskItem> items = new ArrayList<>();
    private final List<DownloadGroup> groups = new ArrayList<>();
    private final Set<String> deletedUrls = new HashSet<>();
    private TaskAdapter adapter;
    private ListView list;
    private TextView title;
    private boolean showingEpisodes = true;
    private DownloadGroup currentGroup;
    private String deletingUrl;
    private boolean multiSelect;
    private final Set<String> selectedUrls = new HashSet<>();
    private LinearLayout batchBar;
    private TextView confirmBatch;

    public static void start(Activity activity) {
        activity.startActivity(new android.content.Intent(activity, DownloadActivity.class));
    }

    @Override
    protected void onCreate(@Nullable Bundle state) {
        // Match BaseActivity: draw the same wallpaper and transparent system bars as HomeActivity.
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT));
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        // Match BaseActivity: apply the user's selected dynamic palette before the
        // Activity resolves its Material 3 theme attributes.
        int dynamicColor = Setting.getDynamicColor();
        if (dynamicColor != 0) {
            DynamicColors.applyToActivityIfAvailable(this, new DynamicColorsOptions.Builder().setContentBasedSource(dynamicColor).build());
        }
        super.onCreate(state);
        // Use the app window background for the page, not the always-light surface tone.
        surfaceColor = MaterialColors.getColor(this, android.R.attr.colorBackground, Color.BLACK);
        cardColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant, surfaceColor);
        primaryColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, Color.WHITE);
        textColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
        mutedColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, textColor);
        buttonColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer, cardColor);
        buttonTextColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSecondaryContainer, textColor);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.setBackgroundColor(Color.TRANSPARENT);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            root.setPadding(dp(16), dp(12) + top, dp(16), dp(12));
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(32);
        back.setTextColor(textColor);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(buttonColor, dp(12)));
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        back.setOnClickListener(v -> finish());

        title = new TextView(this);
        title.setText("下载");
        title.setTextSize(21);
        title.setTextColor(textColor);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dp(14), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView deleteAll = new ImageView(this);
        deleteAll.setImageResource(com.fongmi.android.tv.R.drawable.ic_download_delete);
        deleteAll.setColorFilter(textColor);
        deleteAll.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        deleteAll.setPadding(dp(8), dp(8), dp(8), dp(8));
        deleteAll.setContentDescription("清空下载列表");
        deleteAll.setBackgroundColor(Color.TRANSPARENT);
        header.addView(deleteAll, new LinearLayout.LayoutParams(dp(42), dp(42)));
        deleteAll.setOnClickListener(v -> toggleBatchMode());
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));

        list = new ListView(this);
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(9));
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setPadding(0, dp(2), 0, dp(8));
        list.setClipToPadding(false);
        adapter = new TaskAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        batchBar = new LinearLayout(this);
        batchBar.setGravity(Gravity.CENTER_VERTICAL);
        batchBar.setPadding(dp(4), dp(8), dp(4), dp(4));
        batchBar.setVisibility(View.GONE);
        TextView cancelBatch = new TextView(this);
        cancelBatch.setText("取消"); cancelBatch.setTextColor(buttonTextColor); cancelBatch.setTextSize(16); cancelBatch.setGravity(Gravity.CENTER); cancelBatch.setBackground(round(buttonColor, dp(12)));
        batchBar.addView(cancelBatch, new LinearLayout.LayoutParams(0, dp(46), 1));
        confirmBatch = new TextView(this);
        confirmBatch.setText("确定 (0)"); confirmBatch.setTextColor(Color.WHITE); confirmBatch.setTextSize(16); confirmBatch.setGravity(Gravity.CENTER); confirmBatch.setBackground(round(primaryColor, dp(12)));
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, dp(46), 1); confirmParams.leftMargin = dp(12);
        batchBar.addView(confirmBatch, confirmParams);
        root.addView(batchBar, new LinearLayout.LayoutParams(-1, dp(62)));
        cancelBatch.setOnClickListener(v -> exitBatchMode()); confirmBatch.setOnClickListener(v -> deleteSelected());
        list.setOnItemClickListener((parent, view, position, id) -> {
            Object item = adapter.getItem(position);
            if (!(item instanceof VideoTaskItem task)) return;
            if (multiSelect) { toggleSelected(task); return; }
            if (!TextUtils.isEmpty(deletingUrl)) {
                if (TextUtils.equals(deletingUrl, task.getUrl())) return;
                else {
                    deletingUrl = null;
                    adapter.notifyDataSetChanged();
                    onEpisodeClick(task);
                }
            } else onEpisodeClick(task);
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            Object item = adapter.getItem(position);
            if (!(item instanceof VideoTaskItem task)) return false;
            if (multiSelect) return true;
            deletingUrl = TextUtils.equals(deletingUrl, task.getUrl()) ? null : task.getUrl();
            adapter.notifyDataSetChanged();
            return true;
        });

        setContentView(root);
        // BaseActivity inserts CustomWallView behind the page content. Do the same here.
        ((ViewGroup) findViewById(android.R.id.content)).addView(
                new CustomWallView(this, null), 0,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ViewCompat.requestApplyInsets(root);
        VideoDownloadManager.getInstance().setGlobalDownloadListener(listener);
        VideoDownloadManager.getInstance().fetchDownloadItems(infosCallback);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void onEpisodeClick(VideoTaskItem task) {
        if (task.isSuccessState()) play(task);
        else toggle(task);
    }

    private void play(VideoTaskItem task) {
        String path = task.getFilePath();
        if (TextUtils.isEmpty(path)) {
            Notify.show("本地视频路径为空，请重新下载该集");
            return;
        }
        File file = new File(path);
        if (!file.isFile() || !file.canRead() || file.length() <= 0) {
            Notify.show("下载文件不存在或不完整，请删除后重新下载");
            return;
        }
        String displayName = episodeName(task);
        openDownloadedSeries(task);
    }


    private void openDownloadedSeries(VideoTaskItem selected) {
        String groupName = selected.getGroupName();
        if (TextUtils.isEmpty(groupName)) groupName = episodeName(selected);
        List<VideoTaskItem> completed = new ArrayList<>();
        for (VideoTaskItem item : items) {
            if (item.isSuccessState() && TextUtils.equals(groupName, item.getGroupName())
                    && !TextUtils.isEmpty(item.getFilePath())) {
                File local = new File(item.getFilePath());
                if (local.isFile() && local.canRead() && local.length() > 0) completed.add(item);
            }
        }
        completed.sort((a, b) -> {
            int na = episodeNumber(a);
            int nb = episodeNumber(b);
            if (na >= 0 && nb >= 0) return Integer.compare(na, nb);
            return episodeName(a).compareToIgnoreCase(episodeName(b));
        });
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> paths = new ArrayList<>();
        int selectedIndex = 0;
        for (int i = 0; i < completed.size(); i++) {
            VideoTaskItem item = completed.get(i);
            names.add(episodeLabel(item));
            paths.add(item.getFilePath());
            if (TextUtils.equals(item.getUrl(), selected.getUrl())) selectedIndex = i;
        }
        if (paths.isEmpty()) {
            Notify.show("没有找到已完成的本地剧集");
            return;
        }
        VideoActivity.localPlaylist(this, groupName, selected.getCoverUrl(), names, paths, selectedIndex);
    }

    private int episodeNumber(VideoTaskItem item) {
        String value = item.getTitle();
        if (TextUtils.isEmpty(value)) value = item.getFileName();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(value == null ? "" : value);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private String episodeLabel(VideoTaskItem item) {
        String value = item.getTitle();
        String group = item.getGroupName();
        if (!TextUtils.isEmpty(value) && !TextUtils.isEmpty(group) && value.startsWith(group)) value = value.substring(group.length()).trim();
        if (TextUtils.isEmpty(value)) value = item.getFileName();
        return TextUtils.isEmpty(value) ? "未知集数" : value;
    }

    private void showGroups() {
        finish();
    }

    @Override
    public void onBackPressed() {
        if (multiSelect) exitBatchMode();
        else if (!TextUtils.isEmpty(deletingUrl)) { deletingUrl = null; adapter.notifyDataSetChanged(); }
        else finish();
    }

    private void toggleBatchMode() {
        if (multiSelect) {
            exitBatchMode();
            return;
        }
        // An empty download list has nothing to select; leave the screen unchanged.
        if (items.isEmpty() || currentGroup == null || currentGroup.items.isEmpty()) return;
        deletingUrl = null;
        multiSelect = true;
        selectedUrls.clear();
        batchBar.setVisibility(View.VISIBLE);
        updateBatchCount();
        adapter.notifyDataSetChanged();
    }
    private void exitBatchMode() { multiSelect = false; selectedUrls.clear(); batchBar.setVisibility(View.GONE); adapter.notifyDataSetChanged(); }
    private void toggleSelected(VideoTaskItem item) {
        String url = item.getUrl(); if (TextUtils.isEmpty(url)) return;
        if (!selectedUrls.add(url)) selectedUrls.remove(url);
        updateBatchCount(); adapter.notifyDataSetChanged();
    }
    private void updateBatchCount() { if (confirmBatch != null) confirmBatch.setText("确定 (" + selectedUrls.size() + ")"); }
    private void deleteSelected() {
        if (selectedUrls.isEmpty()) { Notify.show("请先选择要删除的记录"); return; }
        List<VideoTaskItem> copy = new ArrayList<>();
        for (VideoTaskItem item : items) if (selectedUrls.contains(item.getUrl())) copy.add(item);
        exitBatchMode(); for (VideoTaskItem item : copy) delete(item, true);
        Notify.show("已删除 " + copy.size() + " 条下载记录及本地文件");
    }

    private void toggle(VideoTaskItem item) {
        if (item.isRunningTask() || item.isPendingTask()) {
            VideoDownloadManager.getInstance().pauseDownloadTask(item.getUrl());
        } else {
            VideoDownloadManager.getInstance().resumeDownload(item.getUrl());
        }
    }

    private void delete(VideoTaskItem item, boolean deleteFile) {
        String url = item.getUrl();
        if (TextUtils.equals(deletingUrl, url)) deletingUrl = null;
        if (!TextUtils.isEmpty(url)) deletedUrls.add(url);
        // The SDK resets the item and emits onDownloadDefault after deleting it.
        // That callback must remove the row, never reinsert the now-empty item.
        VideoDownloadManager.getInstance().deleteVideoTask(item, deleteFile);
        removeItem(item);
    }

    private void removeItem(VideoTaskItem item) {
        String url = item.getUrl();
        Iterator<VideoTaskItem> iterator = items.iterator();
        while (iterator.hasNext()) {
            VideoTaskItem value = iterator.next();
            if (TextUtils.equals(value.getUrl(), url)) iterator.remove();
        }
        rebuildGroups();
    }

    private void update(VideoTaskItem item) {
        runOnUiThread(() -> {
            if (TextUtils.isEmpty(item.getUrl()) || deletedUrls.contains(item.getUrl())) return;
            int index = -1;
            for (int i = 0; i < items.size(); i++) {
                if (TextUtils.equals(items.get(i).getUrl(), item.getUrl())) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) items.set(index, item);
            else items.add(0, item);
            rebuildGroups();
        });
    }

    private void rebuildGroups() {
        groups.clear();
        if (!containsUrl(deletingUrl)) deletingUrl = null;
        DownloadGroup all = new DownloadGroup("下载", items.isEmpty() ? "" : items.get(0).getCoverUrl());
        for (VideoTaskItem item : items) {
            if (!TextUtils.isEmpty(item.getUrl())) all.items.add(item);
        }
        if (!all.items.isEmpty()) groups.add(all);
        currentGroup = all;
        showingEpisodes = true;
        if (title != null) title.setText("下载");
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private boolean containsUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        for (VideoTaskItem item : items) if (TextUtils.equals(url, item.getUrl())) return true;
        return false;
    }

    private final DownloadListener listener = new DownloadListener() {
        @Override public void onDownloadDefault(VideoTaskItem item) {
            runOnUiThread(() -> {
                if (!TextUtils.isEmpty(item.getUrl())) deletedUrls.add(item.getUrl());
                removeItem(item);
            });
        }
        @Override public void onDownloadPending(VideoTaskItem item) {
            if (!TextUtils.isEmpty(item.getUrl())) deletedUrls.remove(item.getUrl());
            update(item);
        }
        @Override public void onDownloadPrepare(VideoTaskItem item) { update(item); }
        @Override public void onDownloadStart(VideoTaskItem item) { update(item); }
        @Override public void onDownloadProgress(VideoTaskItem item) { update(item); }
        @Override public void onDownloadSpeed(VideoTaskItem item) { update(item); }
        @Override public void onDownloadPause(VideoTaskItem item) { update(item); }
        @Override public void onDownloadError(VideoTaskItem item) { update(item); }
        @Override public void onDownloadSuccess(VideoTaskItem item) { update(item); }
    };

    private final IDownloadInfosCallback infosCallback = infos -> runOnUiThread(() -> {
        items.clear();
        items.addAll(infos);
        rebuildGroups();
    });

    @Override
    protected void onDestroy() {
        VideoDownloadManager.getInstance().removeDownloadInfosCallback(infosCallback);
        VideoDownloadManager.getInstance().setGlobalDownloadListener(new DownloadListener());
        super.onDestroy();
    }

    private class TaskAdapter extends BaseAdapter {
        @Override public int getCount() {
            if (showingEpisodes) return currentGroup == null ? 0 : currentGroup.items.size();
            return groups.size();
        }

        @Override public Object getItem(int position) {
            return showingEpisodes ? currentGroup.items.get(position) : groups.get(position);
        }

        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row row;
            if (convertView == null) {
                FrameLayout card = new FrameLayout(DownloadActivity.this);
                LinearLayout line = new LinearLayout(DownloadActivity.this);
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(dp(12), dp(12), dp(12), dp(12));
                line.setBackground(round(cardColor, dp(16)));
                TextView selectDot = new TextView(DownloadActivity.this); selectDot.setGravity(Gravity.CENTER); selectDot.setTextSize(14);
                LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(24), dp(24)); dotParams.rightMargin = dp(10); line.addView(selectDot, dotParams);
                ImageView cover = new ImageView(DownloadActivity.this); cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                line.addView(cover, new LinearLayout.LayoutParams(dp(96), dp(72)));
                LinearLayout texts = new LinearLayout(DownloadActivity.this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setPadding(dp(14), 0, 0, 0);
                line.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
                TextView name = new TextView(DownloadActivity.this);
                name.setTextColor(textColor);
                name.setTextSize(16);
                name.setMaxLines(1);
                name.setEllipsize(TextUtils.TruncateAt.END);
                name.setTypeface(null, android.graphics.Typeface.BOLD);
                TextView state = new TextView(DownloadActivity.this);
                state.setTextColor(mutedColor);
                state.setTextSize(13);
                state.setPadding(0, dp(5), 0, 0);
                ProgressBar bar = new ProgressBar(DownloadActivity.this, null, android.R.attr.progressBarStyleHorizontal);
                bar.setMax(100);
                bar.setProgressTintList(android.content.res.ColorStateList.valueOf(primaryColor));
                bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(buttonColor));
                LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, dp(4));
                barParams.topMargin = dp(8);
                TextView progress = new TextView(DownloadActivity.this);
                progress.setTextColor(mutedColor);
                progress.setTextSize(12);
                progress.setPadding(0, dp(4), 0, 0);
                texts.addView(name);
                texts.addView(state);
                texts.addView(bar, barParams);
                texts.addView(progress);
                FrameLayout deleteOverlay = new FrameLayout(DownloadActivity.this);
                deleteOverlay.setBackground(round(Color.argb(155, 0, 0, 0), dp(16)));
                ImageView deleteIcon = new ImageView(DownloadActivity.this);
                deleteIcon.setImageResource(com.fongmi.android.tv.R.drawable.ic_download_delete);
                deleteIcon.setColorFilter(Color.WHITE);
                deleteOverlay.addView(deleteIcon, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
                deleteOverlay.setVisibility(View.GONE);
                card.addView(line, new FrameLayout.LayoutParams(-1, -2));
                card.addView(deleteOverlay, new FrameLayout.LayoutParams(-1, -1));
                row = new Row(cover, name, state, progress, bar, deleteOverlay, deleteIcon, selectDot);
                card.setTag(row);
                convertView = card;
                convertView.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
            } else {
                row = (Row) convertView.getTag();
            }

            if (showingEpisodes) {
                VideoTaskItem task = currentGroup.items.get(position);
                boolean deleting = TextUtils.equals(deletingUrl, task.getUrl());
                boolean selected = selectedUrls.contains(task.getUrl()); row.selectDot.setVisibility(multiSelect ? View.VISIBLE : View.GONE); row.selectDot.setText(selected ? "✓" : ""); row.selectDot.setTextColor(Color.WHITE);
                GradientDrawable dot = new GradientDrawable(); dot.setShape(GradientDrawable.OVAL);
                if (selected) dot.setColor(primaryColor); else { dot.setColor(Color.TRANSPARENT); dot.setStroke(dp(1.5f), mutedColor); }
                row.selectDot.setBackground(dot);
                if (deleting) {
                    if (row.deleteOverlay.getVisibility() != View.VISIBLE) {
                        row.deleteOverlay.setAlpha(0f);
                        row.deleteIcon.setScaleX(0.65f);
                        row.deleteIcon.setScaleY(0.65f);
                    }
                    row.deleteOverlay.setVisibility(View.VISIBLE); row.deleteOverlay.setClickable(true); row.deleteOverlay.setOnClickListener(v -> { }); row.deleteIcon.setOnClickListener(v -> delete(task, true));
                    row.deleteOverlay.animate().alpha(1f).setDuration(180).start();
                    row.deleteIcon.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
                } else {
                    row.deleteOverlay.animate().cancel();
                    row.deleteIcon.animate().cancel();
                    row.deleteOverlay.setVisibility(View.GONE); row.deleteOverlay.setClickable(false); row.deleteIcon.setOnClickListener(null);
                    row.deleteOverlay.setAlpha(1f);
                    row.deleteIcon.setScaleX(1f);
                    row.deleteIcon.setScaleY(1f);
                }
                row.name.setText(episodeName(task));
                row.state.setText(state(task));
                row.state.setTextColor(task.isSuccessState() ? primaryColor : task.isErrorState() ? Color.RED : mutedColor);
                row.bar.setProgress(task.isSuccessState() ? 100 : Math.max(0, Math.min(100, Math.round(task.getPercent()))));
                row.progress.setText(task.getSpeedString() + "  ·  " + task.getDownloadSizeString() + "  ·  " + task.getPercentString());
                Glide.with(DownloadActivity.this).load(task.getCoverUrl()).centerCrop().into(row.cover);
            } else {
                row.deleteOverlay.setVisibility(View.GONE); row.selectDot.setVisibility(View.GONE);
                DownloadGroup group = groups.get(position);
                int completed = 0;
                int running = 0;
                for (VideoTaskItem task : group.items) {
                    if (task.isSuccessState()) completed++;
                    if (task.isRunningTask() || task.isPendingTask()) running++;
                }
                row.name.setText(group.name);
                row.state.setText(completed == group.items.size() ? "全部下载完成" : "已下载 " + completed + " / " + group.items.size() + " 集");
                row.state.setTextColor(completed == group.items.size() ? primaryColor : mutedColor);
                row.progress.setText(running > 0 ? running + " 个任务正在下载  ·  点击查看详情" : "点击查看下载详情");
                Glide.with(DownloadActivity.this).load(group.coverUrl).centerCrop().into(row.cover);
            }
            return convertView;
        }
    }

    private String episodeName(VideoTaskItem item) {
        String value = item.getTitle();
        String group = item.getGroupName();
        if (!TextUtils.isEmpty(value) && !TextUtils.isEmpty(group) && value.startsWith(group)) {
            value = value.substring(group.length()).trim();
        }
        if (TextUtils.isEmpty(value)) value = item.getFileName();
        if (TextUtils.isEmpty(value)) value = "未知集数";
        return TextUtils.isEmpty(group) ? value : group + "  " + value;
    }

    private String state(VideoTaskItem item) {
        if (item.isSuccessState()) return "已完成 · 点击播放";
        if (item.isErrorState()) return "下载失败 · 点击重试";
        if (item.isRunningTask()) return "正在下载 · 点击暂停";
        if (item.isPendingTask()) return "等待下载 · 点击暂停";
        return "已暂停 · 点击继续";
    }

    private static class DownloadGroup {
        final String name;
        final String coverUrl;
        final List<VideoTaskItem> items = new ArrayList<>();
        DownloadGroup(String name, String coverUrl) {
            this.name = name;
            this.coverUrl = coverUrl;
        }
    }

    private static class Row {
        final ImageView cover;
        final TextView name;
        final TextView state;
        final TextView progress;
        final ProgressBar bar;
        final FrameLayout deleteOverlay;
        final ImageView deleteIcon;
        final TextView selectDot;
        Row(ImageView cover, TextView name, TextView state, TextView progress, ProgressBar bar, FrameLayout deleteOverlay, ImageView deleteIcon, TextView selectDot) {
            this.cover = cover;
            this.name = name;
            this.state = state;
            this.progress = progress;
            this.bar = bar;
            this.deleteOverlay = deleteOverlay;
            this.deleteIcon = deleteIcon; this.selectDot = selectDot;
        }
    }
}