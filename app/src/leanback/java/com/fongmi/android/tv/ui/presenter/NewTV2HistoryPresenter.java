package com.fongmi.android.tv.ui.presenter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.AdapterVodHistoryBinding;
import com.fongmi.android.tv.ui.custom.PosterFocusAnimator;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** NewTV2-style recent history cards: 16:9 artwork, episode / played percentage and progress bar. */
public final class NewTV2HistoryPresenter extends HistoryPresenter {

    private static final Pattern EPISODE = Pattern.compile("(?i)(?:s\\d{1,2}e|ep|e)(\\d{1,3})");
    private static final Pattern CHINESE_EPISODE = Pattern.compile("第\\s*(\\d{1,4})\\s*([集话回期篇])");
    private final OnClickListener listener;
    private final int width;
    private final int height;

    public NewTV2HistoryPresenter(OnClickListener listener) {
        super(listener);
        this.listener = listener;
        int[] size = Product.getHistorySpec();
        width = size[0];
        height = size[1];
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        AdapterVodHistoryBinding binding = AdapterVodHistoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        ViewGroup.LayoutParams root = binding.getRoot().getLayoutParams();
        root.width = width;
        binding.getRoot().setLayoutParams(root);
        ViewGroup.LayoutParams poster = binding.poster.getLayoutParams();
        poster.width = width;
        poster.height = height;
        binding.poster.setLayoutParams(poster);
        PosterFocusAnimator.bind(binding.getRoot(), false);
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        Holder holder = (Holder) viewHolder;
        History item = (History) object;
        boolean deleting = isDelete();
        holder.item = item;
        holder.binding.name.setText(item.getVodName());
        holder.binding.delete.setVisibility(deleting ? View.VISIBLE : View.GONE);
        holder.binding.meta.setVisibility(deleting ? View.GONE : View.VISIBLE);

        String remarks = item.getVodName().equals(item.getVodRemarks()) ? "" : item.getVodRemarks().trim();
        holder.episode = parseEpisode(remarks);
        long duration = item.getDuration();
        long position = item.getPosition();
        holder.percent = duration > 0 && position >= 0
                ? Math.max(0, Math.min(100, Math.round(position * 100f / duration))) : -1;
        holder.binding.progress.setVisibility(!deleting && holder.percent >= 0 ? View.VISIBLE : View.GONE);
        holder.binding.progress.setProgress(Math.max(0, holder.percent));
        holder.updateMeta();

        holder.binding.getRoot().setOnLongClickListener(view -> listener.onLongClick());
        holder.binding.getRoot().setOnClickListener(view -> {
            if (isDelete()) listener.onItemDelete(item);
            else listener.onItemClick(item);
        });
        ImgUtil.load(item.getVodName(), item.getVodPic(), holder.binding.image);
    }

    private static String parseEpisode(String remarks) {
        if (TextUtils.isEmpty(remarks)) return "";
        Matcher cn = CHINESE_EPISODE.matcher(remarks);
        if (cn.find()) return "第" + cn.group(1) + cn.group(2);
        Matcher matcher = EPISODE.matcher(remarks);
        if (matcher.find()) return "第" + matcher.group(1) + "集";
        if (remarks.matches("\\d{1,3}")) return "第" + Integer.parseInt(remarks) + "集";
        return remarks.length() > 8 ? remarks.substring(0, 8) : remarks;
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
        Holder holder = (Holder) viewHolder;
        holder.item = null;
        PosterFocusAnimator.reset(holder.binding.getRoot());
        Glide.with(holder.binding.image).clear(holder.binding.image);
    }

    private static final class Holder extends Presenter.ViewHolder {
        final AdapterVodHistoryBinding binding;
        History item;
        String episode = "";
        int percent = -1;

        Holder(AdapterVodHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void updateMeta() {
            if (percent >= 0) {
                String played = "已播:" + percent + "%";
                binding.meta.setText(TextUtils.isEmpty(episode) ? played : episode + " · " + played);
            } else {
                binding.meta.setText(episode);
            }
        }
    }
}