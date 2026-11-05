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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.MaterialColors;

import com.bumptech.glide.Glide;
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
    private boolean showingEpisodes;
    private DownloadGroup currentGroup;

    public static void start(Activity activity) {
        activity.startActivity(new android.content.Intent(activity, DownloadActivity.class));
    }

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        // Read Material 3 colors from the app's active Day/Night theme so this screen
        // follows the same dynamic palette as the rest of the mobile app.
        surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.BLACK);
        cardColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant, surfaceColor);
        primaryColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, Color.WHITE);
        textColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
        mutedColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, textColor);
        buttonColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer, cardColor);
        buttonTextColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSecondaryContainer, textColor);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.setBackgroundColor(surfaceColor);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(34);
        back.setTextColor(textColor);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(buttonColor, dp(12)));
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        back.setOnClickListener(v -> showGroups());

        title = new TextView(this);
        title.setText("下载管理");
        title.setTextSize(21);
        title.setTextColor(textColor);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dp(14), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView hint = new TextView(this);
        hint.setText("已下载的视频和正在下载的任务");
        hint.setTextSize(13);
        hint.setTextColor(mutedColor);
        hint.setPadding(dp(58), 0, 0, dp(14));
        root.addView(hint);

        TextView pauseAll = new TextView(this);
        pauseAll.setText("暂停全部任务");
        pauseAll.setTextSize(13);
        pauseAll.setTextColor(buttonTextColor);
        pauseAll.setGravity(Gravity.CENTER);
        pauseAll.setPadding(dp(16), 0, dp(16), 0);
        pauseAll.setBackground(round(buttonColor, dp(18)));
        LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(-2, dp(38));
        pauseParams.bottomMargin = dp(12);
        root.addView(pauseAll, pauseParams);
        pauseAll.setOnClickListener(v -> VideoDownloadManager.getInstance().pauseAllDownloadTasks());

        list = new ListView(this);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(9));
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setPadding(0, dp(2), 0, dp(8));
        list.setClipToPadding(false);
        adapter = new TaskAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener((parent, view, position, id) -> {
            Object item = adapter.getItem(position);
            if (item instanceof DownloadGroup group) openGroup(group);
            else if (item instanceof VideoTaskItem task) onEpisodeClick(task);
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            Object item = adapter.getItem(position);
            if (!(item instanceof VideoTaskItem task)) return false;
            new AlertDialog.Builder(this)
                    .setTitle("删除下载任务")
                    .setMessage("是否同时删除已下载的本地文件？")
                    .setNegativeButton("取消", null)
                    .setNeutralButton("仅删除记录", (dialog, which) -> delete(task, false))
                    .setPositiveButton("删除文件和记录", (dialog, which) -> delete(task, true))
                    .show();
            return true;
        });

        setContentView(root);
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
        String displayName = task.getGroupName();
        if (TextUtils.isEmpty(displayName)) displayName = task.getTitle();
        VideoActivity.file(this, Uri.fromFile(file), displayName, task.getCoverUrl());
    }

    private void openGroup(DownloadGroup group) {
        currentGroup = group;
        showingEpisodes = true;
        title.setText(group.name);
        adapter.notifyDataSetChanged();
    }

    private void showGroups() {
        if (!showingEpisodes) {
            finish();
            return;
        }
        showingEpisodes = false;
        currentGroup = null;
        title.setText("下载管理");
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onBackPressed() {
        if (showingEpisodes) showGroups();
        else super.onBackPressed();
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
        String currentName = showingEpisodes && currentGroup != null ? currentGroup.name : null;
        Map<String, DownloadGroup> grouped = new LinkedHashMap<>();
        for (VideoTaskItem item : items) {
            String groupName = item.getGroupName();
            if (TextUtils.isEmpty(groupName)) groupName = item.getTitle();
            // A reset/deleted SDK item has empty metadata. Do not turn its MD5
            // local filename into a fake "unknown" series group.
            if (TextUtils.isEmpty(groupName)) continue;
            DownloadGroup group = grouped.get(groupName);
            if (group == null) {
                group = new DownloadGroup(groupName, item.getCoverUrl());
                grouped.put(groupName, group);
            }
            group.items.add(item);
        }
        groups.clear();
        groups.addAll(grouped.values());
        if (currentName != null) {
            currentGroup = null;
            for (DownloadGroup group : groups) {
                if (TextUtils.equals(group.name, currentName)) {
                    currentGroup = group;
                    break;
                }
            }
            if (currentGroup == null) {
                showingEpisodes = false;
                title.setText("下载管理");
            }
        }
        adapter.notifyDataSetChanged();
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
                LinearLayout line = new LinearLayout(DownloadActivity.this);
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(dp(12), dp(12), dp(12), dp(12));
                line.setBackground(round(cardColor, dp(16)));
                ImageView cover = new ImageView(DownloadActivity.this);
                cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
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
                TextView progress = new TextView(DownloadActivity.this);
                progress.setTextColor(mutedColor);
                progress.setTextSize(12);
                progress.setPadding(0, dp(3), 0, 0);
                texts.addView(name);
                texts.addView(state);
                texts.addView(progress);
                row = new Row(cover, name, state, progress);
                line.setTag(row);
                convertView = line;
                convertView.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
            } else {
                row = (Row) convertView.getTag();
            }

            if (showingEpisodes) {
                VideoTaskItem task = currentGroup.items.get(position);
                row.name.setText(episodeName(task));
                row.state.setText(state(task));
                row.state.setTextColor(task.isSuccessState() ? primaryColor : task.isErrorState() ? Color.RED : mutedColor);
                row.progress.setText(task.getPercentString() + "  ·  " + task.getDownloadSizeString());
                Glide.with(DownloadActivity.this).load(task.getCoverUrl()).centerCrop().into(row.cover);
            } else {
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
        return TextUtils.isEmpty(value) ? "未知集数" : value;
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
        Row(ImageView cover, TextView name, TextView state, TextView progress) {
            this.cover = cover;
            this.name = name;
            this.state = state;
            this.progress = progress;
        }
    }
}