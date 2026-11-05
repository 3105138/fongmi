package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.ui.activity.VideoActivity;
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

public class DownloadActivity extends AppCompatActivity {

    private final List<VideoTaskItem> items = new ArrayList<>();
    private final List<DownloadGroup> groups = new ArrayList<>();
    private final List<?> visibleItems = new ArrayList<>();
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);
        root.setBackgroundColor(0xFF171717);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(this);
        back.setText("返回");
        back.setOnClickListener(v -> showGroups());
        header.addView(back, new LinearLayout.LayoutParams(-2, -2));
        title = new TextView(this);
        title.setText("下载列表");
        title.setTextSize(21);
        title.setTextColor(0xFFFFFFFF);
        title.setPadding(12, 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        Button pauseAll = new Button(this);
        pauseAll.setText("暂停全部下载");
        pauseAll.setOnClickListener(v -> VideoDownloadManager.getInstance().pauseAllDownloadTasks());
        root.addView(pauseAll, new LinearLayout.LayoutParams(-1, -2));

        list = new ListView(this);
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

    private void onEpisodeClick(VideoTaskItem task) {
        if (task.isSuccessState()) {
            play(task);
        } else {
            toggle(task);
        }
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
        VideoActivity.file(this, Uri.fromFile(file));
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
        title.setText("下载列表");
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
        VideoDownloadManager.getInstance().deleteVideoTask(item, deleteFile);
        items.remove(item);
        rebuildGroups();
    }

    private void update(VideoTaskItem item) {
        runOnUiThread(() -> {
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
        Map<String, DownloadGroup> grouped = new LinkedHashMap<>();
        for (VideoTaskItem item : items) {
            String groupName = item.getGroupName();
            if (TextUtils.isEmpty(groupName)) groupName = item.getTitle();
            if (TextUtils.isEmpty(groupName)) groupName = item.getFileName();
            if (TextUtils.isEmpty(groupName)) groupName = "未命名视频";
            DownloadGroup group = grouped.get(groupName);
            if (group == null) {
                group = new DownloadGroup(groupName, item.getCoverUrl());
                grouped.put(groupName, group);
            }
            group.items.add(item);
        }
        groups.clear();
        groups.addAll(grouped.values());
        adapter.notifyDataSetChanged();
    }

    private final DownloadListener listener = new DownloadListener() {
        @Override public void onDownloadDefault(VideoTaskItem item) { update(item); }
        @Override public void onDownloadPending(VideoTaskItem item) { update(item); }
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
                line.setPadding(8, 12, 8, 12);
                ImageView cover = new ImageView(DownloadActivity.this);
                cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                line.addView(cover, new LinearLayout.LayoutParams(104, 72));
                LinearLayout texts = new LinearLayout(DownloadActivity.this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setPadding(12, 0, 0, 0);
                line.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
                TextView name = new TextView(DownloadActivity.this);
                name.setTextColor(0xFFFFFFFF);
                name.setTextSize(16);
                TextView state = new TextView(DownloadActivity.this);
                state.setTextColor(0xFFBBBBBB);
                state.setTextSize(13);
                TextView progress = new TextView(DownloadActivity.this);
                progress.setTextColor(0xFFBBBBBB);
                progress.setTextSize(13);
                texts.addView(name);
                texts.addView(state);
                texts.addView(progress);
                row = new Row(cover, name, state, progress);
                line.setTag(row);
                convertView = line;
            } else {
                row = (Row) convertView.getTag();
            }

            if (showingEpisodes) {
                VideoTaskItem task = currentGroup.items.get(position);
                row.name.setText(episodeName(task));
                row.state.setText(state(task));
                row.progress.setText(task.getPercentString() + "  " + task.getDownloadSizeString());
                Glide.with(DownloadActivity.this).load(task.getCoverUrl()).into(row.cover);
            } else {
                DownloadGroup group = groups.get(position);
                int completed = 0;
                for (VideoTaskItem task : group.items) if (task.isSuccessState()) completed++;
                row.name.setText(group.name);
                row.state.setText(completed == group.items.size()
                        ? "已全部下载完成"
                        : "已完成 " + completed + " / " + group.items.size() + " 集");
                row.progress.setText("点击查看已下载和下载中的集数");
                Glide.with(DownloadActivity.this).load(group.coverUrl).into(row.cover);
            }
            return convertView;
        }
    }

    private String episodeName(VideoTaskItem item) {
        String title = item.getTitle();
        String group = item.getGroupName();
        if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(group) && title.startsWith(group)) {
            title = title.substring(group.length()).trim();
        }
        if (TextUtils.isEmpty(title)) title = item.getFileName();
        return TextUtils.isEmpty(title) ? "未知集数" : title;
    }

    private String state(VideoTaskItem item) {
        if (item.isSuccessState()) return "已完成 · 点击播放";
        if (item.isErrorState()) return "下载失败 · 点击重试";
        if (item.isRunningTask()) return "下载中 · 点击暂停";
        if (item.isPendingTask()) return "排队中 · 点击暂停";
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
