package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.splashscreen.SplashScreen;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.api.node.NodeRuntime;
import com.fongmi.android.tv.bean.Cache;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Func;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.NewTV2HeroRow;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.db.BackupManager;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.playback.ExternalPlayback;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.service.DLNARendererService;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.adapter.BaseDiffCallback;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.custom.CustomTitleView;
import com.fongmi.android.tv.ui.custom.PosterFocusAnimator;
import com.fongmi.android.tv.ui.dialog.LocalConnectDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.presenter.FuncPresenter;
import com.fongmi.android.tv.ui.presenter.HeaderPresenter;
import com.fongmi.android.tv.ui.presenter.HistoryPresenter;
import com.fongmi.android.tv.ui.presenter.NewTV2HeroPresenter;
import com.fongmi.android.tv.ui.presenter.ProgressPresenter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.utils.NewTV2HomeData;
import com.github.catvod.net.OkHttp;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.common.collect.Lists;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class HomeActivity extends BaseActivity implements CustomTitleView.Listener, VodPresenter.OnClickListener, FuncPresenter.OnClickListener, HistoryPresenter.OnClickListener {

    private static final String STATE_ACTION_HANDLED = "action_handled";
    private static volatile int instanceCount;

    public static boolean isRunning() {
        return instanceCount > 0;
    }

    private ActivityHomeBinding mBinding;
    private ArrayObjectAdapter mHistoryAdapter;
    private ArrayObjectAdapter mFuncAdapter;
    private ArrayObjectAdapter mAdapter;
    private HistoryPresenter mPresenter;
    private VodPresenter mVodPresenter;
    private SiteViewModel mViewModel;
    private Intent pendingAction;
    private boolean actionReady;
    private Result mResult;
    private Clock mClock;
    private NewTV2HeroRow mNewTV2HeroRow;
    private NewTV2HeroPresenter mNewTV2HeroPresenter;

    private final Handler mNewTV2HeroHandler = new Handler(Looper.getMainLooper());
    private final List<NewTV2HomeData.HeroItem> mNewTV2Heroes = new ArrayList<>();
    private boolean mNewTV2HomeActive;
    private int mNewTV2HeroIndex;
    private final Runnable mNewTV2HeroCycle = new Runnable() {
        @Override public void run() {
            if (mBinding == null || !mNewTV2HomeActive || !Setting.isNewTV2Theme() || isFinishing() || isDestroyed() || mNewTV2Heroes.isEmpty()) return;
            mNewTV2HeroIndex = (mNewTV2HeroIndex + 1) % mNewTV2Heroes.size();
            showNewTV2Hero(mNewTV2Heroes.get(mNewTV2HeroIndex));
            mNewTV2HeroHandler.postDelayed(this, 7000L);
        }
    };
    private final ArrayObjectAdapter[] mNewTV2BoardAdapters = new ArrayObjectAdapter[4];
    private final boolean[] mNewTV2BoardRowsAdded = new boolean[4];
    private static final int[] NEWTV2_BOARD_TITLES = {
            R.string.newtv2_board_douban_movie, R.string.newtv2_board_douban_series,
            R.string.newtv2_board_tmdb_movie, R.string.newtv2_board_tmdb_tv
    };

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    private Config getConfig() {
        return VodConfig.get().getConfig();
    }

    @Override
    protected ViewBinding getBinding() {
        int layout = Setting.isNewTV2Theme() ? R.layout.activity_home_newtv2 : R.layout.activity_home;
        View root = LayoutInflater.from(this).inflate(layout, null, false);
        return mBinding = ActivityHomeBinding.bind(root);
    }

    /** Relaunch the existing TV home using the newly selected layout. */
    public static void restartForTheme(Activity activity) {
        Intent intent = new Intent(activity, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingAction = intent;
        consumeAction();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != ExternalPlayback.REQUEST_CODE) return;
        setResult(resultCode, data);
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        instanceCount++;
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        if (savedInstanceState == null || !savedInstanceState.getBoolean(STATE_ACTION_HANDLED)) pendingAction = getIntent();
        mResult = Result.empty();
        mClock = Clock.create(mBinding.clock);
        mBinding.progressLayout.showProgress();
        Updater.get().start(this, false);
        setRecyclerView();
        setViewModel();
        setAdapter();
        checkPermission();
    }

    @Override
    protected void initEvent() {
        mBinding.title.setListener(this);
        if (Setting.isNewTV2Theme()) setNewTV2Navigation();
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (Setting.isNewTV2Theme()) mBinding.toolbar.setVisibility(View.VISIBLE); else mBinding.toolbar.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
                if (Setting.isNewTV2Theme()) updateNewTV2BackdropVisibility(position);
                if (mPresenter.isDelete()) setHistoryDelete(false);
            }
        });
    }

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction()) || Intent.ACTION_VIEW.equals(intent.getAction())) {
            ExternalPlayback.open(this, intent, this::loadLive);
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String keyword = intent.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(keyword)) SearchActivity.start(this, keyword);
        }
    }

    private void consumeAction() {
        if (!actionReady || pendingAction == null) return;
        Intent intent = pendingAction;
        pendingAction = null;
        checkAction(intent);
    }

    @SuppressLint("RestrictedApi")
    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(Integer.class, new HeaderPresenter());
        selector.addPresenter(String.class, new ProgressPresenter());
        if (Setting.isNewTV2Theme()) {
            mNewTV2HeroPresenter = new NewTV2HeroPresenter(this::onNewTV2HeroClick);
            selector.addPresenter(NewTV2HeroRow.class, mNewTV2HeroPresenter);
        }
        selector.addPresenter(Vod.class, new VodPresenter(this, Style.list()));
        int rowZoom = Setting.isNewTV2Theme() ? FocusHighlight.ZOOM_FACTOR_NONE : FocusHighlight.ZOOM_FACTOR_SMALL;
        int historyScroll = Setting.isNewTV2Theme() ? HorizontalGridView.FOCUS_SCROLL_ITEM : HorizontalGridView.FOCUS_SCROLL_ALIGNED;
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16, rowZoom), VodPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16, rowZoom), FuncPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16, rowZoom, historyScroll), HistoryPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getAction().observe(this, result -> WebActivity.handleAction(this, result));
        mViewModel.getResult().observe(this, this::onResult);
    }

    private void onResult(Result result) {
        mAdapter.remove("progress");
        addVideo(mResult = result);
        updateNewTV2Hero(result);
        Cache.clear().put(result);
    }

    /** Use the current configured source only when the TMDB-enriched feed is unavailable. */
    private void updateNewTV2Hero(Result result) {
        if (!Setting.isNewTV2Theme() || !mNewTV2Heroes.isEmpty() || result == null || result.getList().isEmpty()) return;
        Vod item = result.getList().get(0);
        NewTV2HomeData.HeroItem fallback = new NewTV2HomeData.HeroItem(
                item.getName(), item.getContent(), item.getYear(), item.getTypeName(),
                item.getRemarks(), item.getPic(), "", item.getPic());
        showNewTV2Hero(fallback);
    }

    private void loadNewTV2HomeData() {
        if (!Setting.isNewTV2Theme()) return;
        NewTV2HomeData.loadHero(items -> {
            if (!Setting.isNewTV2Theme() || items == null || items.isEmpty()) return;
            mNewTV2Heroes.clear();
            mNewTV2Heroes.addAll(items);
            mNewTV2HeroIndex = 0;
            showNewTV2Hero(mNewTV2Heroes.get(0));
            mNewTV2HeroHandler.removeCallbacks(mNewTV2HeroCycle);
            if (mNewTV2HomeActive) mNewTV2HeroHandler.postDelayed(mNewTV2HeroCycle, 7000L);
        });
        for (int board = 0; board < NEWTV2_BOARD_TITLES.length; board++) {
            final int index = board;
            NewTV2HomeData.loadBoard(index, items -> updateNewTV2Board(index, items));
        }
    }

    private void showNewTV2Hero(NewTV2HomeData.HeroItem item) {
        if (item == null || !Setting.isNewTV2Theme()) return;
        if (mNewTV2HeroRow != null && mAdapter != null) {
            mNewTV2HeroRow.setItem(item);
            int position = mAdapter.indexOf(mNewTV2HeroRow);
            if (position >= 0) mAdapter.notifyArrayItemRangeChanged(position, 1);
        }
        ImageView backdrop = findViewById(R.id.newtv2HeroBackdrop);
        if (backdrop == null) return;
        String path = TextUtils.isEmpty(item.getBackdrop()) ? item.getPoster() : item.getBackdrop();
        if (TextUtils.isEmpty(path)) return;
        Glide.with(this).load(NewTV2HomeData.proxyImage(path, "w1280"))
                .error(NewTV2HomeData.officialImage(path, "w1280"))
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(600))
                .into(backdrop);
    }

    private void onNewTV2HeroClick(NewTV2HomeData.HeroItem item) {
        if (item != null && !TextUtils.isEmpty(item.getTitle())) SearchActivity.start(this, item.getTitle());
    }

    private void updateNewTV2BackdropVisibility(int position) {
        View layer = findViewById(R.id.newtv2BackdropLayer);
        if (layer == null) return;
        int historyHeader = mAdapter == null ? -1 : mAdapter.indexOf(R.string.home_history);
        int historyRow = historyHeader < 0 ? 0 : historyHeader + 1;
        boolean visible = position <= historyRow;
        float alpha = visible ? 1f : 0f;
        layer.animate().cancel();
        layer.animate().alpha(alpha).setDuration(220L).start();
    }

    private void updateNewTV2Board(int board,List<NewTV2HomeData.BoardItem> items) {
        if(!Setting.isNewTV2Theme()||items==null||items.isEmpty()||mAdapter==null)return;
        ArrayObjectAdapter cards=mNewTV2BoardAdapters[board];
        if(cards==null)cards=new ArrayObjectAdapter(new VodPresenter(this,Style.rect()));
        List<Vod> vods=new ArrayList<>(items.size());
        for(NewTV2HomeData.BoardItem item:items){Vod v=new Vod();v.setName(item.getTitle());v.setPic(item.getPoster());v.setRemarks(item.getRating());v.setYear(item.getYear());vods.add(v);}
        cards.setItems(vods,new BaseDiffCallback<Vod>());
        if(!mNewTV2BoardRowsAdded[board]){
            int position=mAdapter.indexOf(R.string.home_recommend);
            for (int later=board+1; later<NEWTV2_BOARD_TITLES.length; later++) if (mNewTV2BoardRowsAdded[later]) position-=2;
            mAdapter.add(position,NEWTV2_BOARD_TITLES[board]);
            mAdapter.add(position+1,new ListRow(cards));
            mNewTV2BoardAdapters[board]=cards;mNewTV2BoardRowsAdded[board]=true;
        }else mNewTV2BoardAdapters[board]=cards;
    }

    /** NewTV2-style navigation reuses the existing FongMi destinations and data. */
    private void setNewTV2Navigation() {
        bindNewTV2Navigation(R.id.newtv2NavHome, () -> {
            mBinding.recycler.scrollToPosition(0);
            mBinding.recycler.requestFocus();
        });
        bindNewTV2Navigation(R.id.newtv2NavVod, () -> VodActivity.start(this, mResult));
        bindNewTV2Navigation(R.id.newtv2NavLive, () -> LiveActivity.start(this));
        bindNewTV2Navigation(R.id.newtv2NavSearch, () -> SearchActivity.start(this));
        bindNewTV2Navigation(R.id.newtv2NavKeep, () -> KeepActivity.start(this));
        bindNewTV2Navigation(R.id.newtv2NavConnect, () -> LocalConnectDialog.show(this, "push"));
        bindNewTV2Navigation(R.id.newtv2NavSetting, () -> SettingActivity.start(this));
        updateNewTV2NavigationAvailability();
        PosterFocusAnimator.bind(mBinding.title, false);
        View home = findViewById(R.id.newtv2NavHome);
        if (home != null) home.setSelected(true);
    }

    private void bindNewTV2Navigation(int id, Runnable action) {
        View view = findViewById(id);
        if (view == null) return;
        view.setOnClickListener(v -> action.run());
        PosterFocusAnimator.bind(view, false);
    }

    private void setAdapter() {
        mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HistoryPresenter(this));
        mFuncAdapter = new ArrayObjectAdapter(new FuncPresenter(this));
        if (Setting.isNewTV2Theme()) {
            mNewTV2HeroRow = new NewTV2HeroRow();
            mAdapter.add(mNewTV2HeroRow);
        } else {
            mAdapter.add(new ListRow(mFuncAdapter));
        }
        mAdapter.add(R.string.home_history);
        mAdapter.add(R.string.home_recommend);
    }

    private void checkPermission() {
        PermissionUtil.requestLocalNetwork(this, this::onNetworkPermissionResult);
    }

    private void onNetworkPermissionResult(boolean allGranted) {
        PermissionUtil.requestNotify(this);
        if (allGranted) DLNARendererService.start(this);
        initConfig();
        setTitle();
        setLogo();
        loadNewTV2HomeData();
    }

    private void setTitle() {
        List<String> items = Arrays.asList(getHome().getName(), getConfig().getName(), getString(R.string.app_name));
        Optional<String> optional = items.stream().filter(s -> !TextUtils.isEmpty(s)).findFirst();
        optional.ifPresent(s -> mBinding.title.setText(s));
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init();
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                onConfigReady();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
                onConfigReady();
            }
        };
    }

    private void onConfigReady() {
        actionReady = true;
        updateNewTV2NavigationAvailability();
        showContent();
        consumeAction();
    }

    private void updateNewTV2NavigationAvailability() {
        if (!Setting.isNewTV2Theme()) return;
        View live = findViewById(R.id.newtv2NavLive);
        if (live != null) live.setVisibility(LiveConfig.hasUrl() ? View.VISIBLE : View.GONE);
    }

    private void showContent() {
        mBinding.progressLayout.showContent();
        setFocus();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(STATE_ACTION_HANDLED, pendingAction == null);
        super.onSaveInstanceState(outState);
    }

    private void loadLive(String url) {
        if (isFinishing() || isDestroyed()) return;
        LiveConfig.load(Config.find(url, 1), new Callback() {
            @Override
            public void success() {
                if (getIntent().getBooleanExtra(ExternalPlayback.FORWARD_RESULT, false)) startActivityForResult(new Intent(HomeActivity.this, LiveActivity.class).putExtra("empty", LiveConfig.isEmpty()), ExternalPlayback.REQUEST_CODE);
                else LiveActivity.start(getActivity());
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
                if (getIntent().getBooleanExtra(ExternalPlayback.FORWARD_RESULT, false)) finish();
            }
        });
    }

    private void setFocus() {
        if (Setting.isNewTV2Theme()) {
            View navHome = findViewById(R.id.newtv2NavHome);
            if (navHome != null && navHome.requestFocus()) return;
        }
        mBinding.title.setSelected(true);
        App.post(() -> mBinding.title.setFocusable(true), 500);
        if (!mBinding.title.hasFocus()) mBinding.recycler.requestFocus();
    }

    private void getVideo() {
        mResult = Result.empty();
        int index = getRecommendIndex();
        boolean gone = mAdapter.indexOf("progress") == -1;
        boolean hasItem = gone && mAdapter.size() > index;
        if (hasItem) mAdapter.removeItems(index, mAdapter.size() - index);
        if (gone) mAdapter.add("progress");
        mViewModel.homeContent();
    }

    private void addVideo(Result result) {
        Style style = result.getStyle(getHome().getStyle());
        if (style.isList()) mAdapter.addAll(mAdapter.size(), result.getList());
        else addGrid(result.getList(), style);
    }

    private void addGrid(List<Vod> items, Style style) {
        List<ListRow> rows = new ArrayList<>();
        if (mVodPresenter == null || !mVodPresenter.matches(style)) mVodPresenter = new VodPresenter(this, style);
        for (List<Vod> part : Lists.partition(items, Product.getColumn(style))) rows.add(createVideoRow(part));
        mAdapter.addAll(mAdapter.size(), rows);
    }

    private ListRow createVideoRow(List<Vod> items) {
        ArrayObjectAdapter adapter = new ArrayObjectAdapter(mVodPresenter);
        adapter.addAll(0, items);
        return new ListRow(adapter);
    }

    private void setFunc() {
        List<Func> items = new ArrayList<>();
        items.add(Func.create(R.string.home_vod));
        if (LiveConfig.hasUrl()) items.add(Func.create(R.string.home_live));
        items.add(Func.create(R.string.home_search));
        items.add(Func.create(R.string.home_keep));
        items.add(Func.create(R.string.home_push));
        items.add(Func.create(R.string.home_setting));
        mFuncAdapter.setItems(items, new BaseDiffCallback<Func>());
    }

    private void getHistory() {
        getHistory(false);
    }

    private void getHistory(boolean renew) {
        List<History> items = History.get();
        int historyIndex = getHistoryIndex();
        int recommendIndex = getRecommendIndex();
        boolean exist = recommendIndex - historyIndex == 2;
        if (renew) mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HistoryPresenter(this));
        if ((items.isEmpty() && exist) || (renew && exist)) mAdapter.removeItems(historyIndex, 1);
        if ((!items.isEmpty() && !exist) || (renew && exist)) mAdapter.add(historyIndex, new ListRow(mHistoryAdapter));
        mHistoryAdapter.setItems(items, new BaseDiffCallback<History>());
    }

    private void setHistoryDelete(boolean delete) {
        mPresenter.setDelete(delete);
        mHistoryAdapter.notifyArrayItemRangeChanged(0, mHistoryAdapter.size());
    }

    private void clearHistory() {
        mAdapter.removeItems(getHistoryIndex(), 1);
        History.clear(VodConfig.getCid());
        mPresenter.setDelete(false);
        mHistoryAdapter.clear();
    }

    private int getHistoryIndex() {
        return mAdapter.indexOf(R.string.home_history) + 1;
    }

    private int getRecommendIndex() {
        return mAdapter.indexOf(R.string.home_recommend) + 1;
    }

    private void setLogo() {
        ImgUtil.logo(mBinding.logo);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        switch (event.type()) {
            case VOD:
                RefreshEvent.history();
                RefreshEvent.home();
                setLogo();
                break;
            case COMMON:
                setFunc();
                break;
            case BOOT:
                LiveActivity.start(this);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case HOME:
                getVideo();
                setTitle();
                break;
            case HISTORY:
                getHistory();
                break;
            case SIZE:
                getVideo();
                getHistory(true);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        switch (event.type()) {
            case SEARCH:
                SearchActivity.start(this, event.text());
                break;
            case PUSH:
                VideoActivity.push(this, event.text());
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (VodConfig.get().getConfig().equals(event.config())) VideoActivity.cast(this, event.history());
        else VodConfig.load(event.config(), getCallback(event));
    }

    private Callback getCallback(CastEvent event) {
        return new Callback() {
            @Override
            public void success() {
                onCastEvent(event);
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    @Override
    public void onItemClick(Func item) {
        if (item.getResId() == R.string.home_vod) VodActivity.start(this, mResult);
        else if (item.getResId() == R.string.home_live) LiveActivity.start(this);
        else if (item.getResId() == R.string.home_keep) KeepActivity.start(this);
        else if (item.getResId() == R.string.home_push) LocalConnectDialog.show(this, "push");
        else if (item.getResId() == R.string.home_search) SearchActivity.start(this);
        else if (item.getResId() == R.string.home_setting) SettingActivity.start(this);
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isAction()) mViewModel.action(getHome().getKey(), item.getAction());
        else if (getHome().isIndex()) CollectActivity.start(this, item.getName());
        else VideoActivity.start(this, getHome().getKey(), item.getId(), item.getName(), item.getPic());
    }

    @Override
    public boolean onLongClick(Vod item) {
        if (item.isAction()) return false;
        CollectActivity.start(this, item.getName());
        return true;
    }

    @Override
    public void onItemClick(History item) {
        VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(History item) {
        mHistoryAdapter.remove(item.delete());
        if (mHistoryAdapter.size() > 0) return;
        mAdapter.removeItems(getHistoryIndex(), 1);
        mPresenter.setDelete(false);
    }

    @Override
    public boolean onLongClick() {
        if (mPresenter.isDelete()) clearHistory();
        else setHistoryDelete(true);
        return true;
    }

    @Override
    public void showDialog() {
        SiteDialog.create().show(this);
    }

    @Override
    public void onRefresh() {
        getVideo();
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) showDialog();
        if (KeyUtil.isActionDown(event) & KeyUtil.isDownKey(event) && getCurrentFocus() == mBinding.title) return mBinding.recycler.getChildAt(0).requestFocus();
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mNewTV2HomeActive = true;
        if (Setting.isNewTV2Theme() && !mNewTV2Heroes.isEmpty()) {
            mNewTV2HeroHandler.removeCallbacks(mNewTV2HeroCycle);
            mNewTV2HeroHandler.postDelayed(mNewTV2HeroCycle, 7000L);
        }
        mClock.start();
    }

    @Override
    protected void onPause() {
        mNewTV2HomeActive = false;
        mNewTV2HeroHandler.removeCallbacks(mNewTV2HeroCycle);
        super.onPause();
        mClock.stop();
    }

    @Override
    protected void onBackInvoked() {
        if (mBinding.progressLayout.isProgress()) {
            showContent();
        } else if (mPresenter.isDelete()) {
            setHistoryDelete(false);
        } else if (mBinding.recycler.getSelectedPosition() != 0) {
            mBinding.recycler.scrollToPosition(0);
        } else {
            if (PlaybackService.isRunning()) Util.moveToBackground(this);
            else super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        mNewTV2HomeActive = false;
        mNewTV2HeroHandler.removeCallbacks(mNewTV2HeroCycle);
        if (--instanceCount == 0 && !isChangingConfigurations()) {
            BackupManager.backup();
            releaseResources();
        }
        super.onDestroy();
    }

    public static void releaseResources() {
        if (isRunning() || PlaybackService.isRunning()) return;
        DLNARendererService.stop(App.get());
        LiveConfig.get().clear();
        VodConfig.get().destroy();
        OkHttp.get().clear();
        Source.get().exit();
        Server.get().stop();
        NodeRuntime.get().releaseIfUnused();
    }
}
