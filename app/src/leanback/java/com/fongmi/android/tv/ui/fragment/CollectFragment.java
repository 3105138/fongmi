package com.fongmi.android.tv.ui.fragment;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.Presenter;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.FragmentTypeBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.activity.VodActivity;
import com.fongmi.android.tv.ui.activity.WebActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SearchMatch;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CollectFragment extends BaseFragment implements CustomScroller.Callback, VodPresenter.OnClickListener {

    private FragmentTypeBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private ArrayObjectAdapter mLast;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private Collect mCollect;
    private String mKeyword;
    private final List<Vod> mAllResults = new ArrayList<>();
    private boolean mShowOtherResults;

    public static CollectFragment newInstance(String keyword, Collect collect) {
        Bundle args = new Bundle();
        args.putString("keyword", keyword);
        CollectFragment fragment = new CollectFragment().setCollect(collect);
        fragment.setArguments(args);
        return fragment;
    }

    private String getKeyword() {
        return mKeyword = mKeyword == null ? getArguments().getString("keyword") : mKeyword;
    }

    private CollectFragment setCollect(Collect collect) {
        this.mCollect = collect;
        return this;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentTypeBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setRecyclerView();
        mBinding.otherResults.setOnClickListener(v -> {
            mShowOtherResults = !mShowOtherResults;
            refreshSearchResults();
        });
        setViewModel();
        addVideo(mCollect);
    }

    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.addOnScrollListener(mScroller = new CustomScroller(this));
        mBinding.recycler.setHeader(getActivity(), R.id.result, R.id.recycler);
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getAction().observe(getViewLifecycleOwner(), result -> WebActivity.handleAction(requireActivity(), result));
        mViewModel.getResult().observe(this, result -> {
            mScroller.endLoading(result);
            addVideo(result.getList());
        });
    }

    private void addVideo(Collect collect) {
        if (collect != null) addVideo(collect.getList());
    }

    public void addVideo(List<Vod> items) {
        if (items == null || getActivity() == null || getActivity().isFinishing()) return;
        mAllResults.addAll(items);
        appendSearchResults(items);
    }

    private void appendSearchResults(List<Vod> items) {
        VodPresenter presenter = new SearchVodPresenter(this);
        for (Vod item : items) {
            if (!mShowOtherResults && !SearchMatch.isExact(getKeyword(), item)) continue;
            if (mLast == null || mLast.size() >= Product.getColumn()) {
                mLast = new ArrayObjectAdapter(presenter);
                mAdapter.add(new ListRow(mLast));
            }
            mLast.add(item);
        }
        updateOtherResults();
    }

    private void refreshSearchResults() {
        List<Vod> exact = mAllResults.stream().filter(item -> SearchMatch.isExact(getKeyword(), item)).collect(Collectors.toList());
        List<Vod> other = mAllResults.stream().filter(item -> !SearchMatch.isExact(getKeyword(), item)).collect(Collectors.toList());
        List<Vod> display = new ArrayList<>(exact);
        if (mShowOtherResults) display.addAll(other);
        mAdapter.clear();
        mLast = null;
        VodPresenter presenter = new VodPresenter(this);
        for (List<Vod> part : Lists.partition(display, Product.getColumn())) mAdapter.add(createVideoRow(part, presenter));
        updateOtherResults();
    }

    private void updateOtherResults() {
        long count = mAllResults.stream().filter(item -> !SearchMatch.isExact(getKeyword(), item)).count();
        mBinding.otherResults.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        mBinding.otherResults.setText(getString(mShowOtherResults ? R.string.search_other_expanded : R.string.search_other_collapsed) + " (" + count + ")");
    }

    private static class SearchVodPresenter extends VodPresenter {

        SearchVodPresenter(OnClickListener listener) {
            super(listener);
        }

        @Override
        public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
            super.onBindViewHolder(viewHolder, object);
            viewHolder.view.setAlpha(0f);
            viewHolder.view.animate().alpha(1f).setDuration(220).start();
        }
    }

    private ListRow createVideoRow(List<Vod> items, VodPresenter presenter) {
        mLast = new ArrayObjectAdapter(presenter);
        mLast.addAll(0, items);
        return new ListRow(mLast);
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isAction()) {
            mViewModel.action(item.getSiteKey(), item.getAction());
            return;
        }
        requireActivity().setResult(Activity.RESULT_OK);
        if (item.isFolder()) VodActivity.start(requireActivity(), item.getSiteKey(), Result.folder(item));
        else VideoActivity.collect(requireActivity(), item.getSiteKey(), item.getId(), item.getName(), item.getPic());
    }

    @Override
    public boolean onLongClick(Vod item) {
        return false;
    }

    @Override
    public boolean onLoadMore(String page) {
        if (mCollect == null || "all".equals(mCollect.getSite().getKey())) return false;
        mViewModel.searchContent(mCollect.getSite(), getKeyword(), false, page);
        return true;
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (mBinding != null && !isVisibleToUser) mBinding.recycler.moveToTop();
    }
}
