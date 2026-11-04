package com.fongmi.android.tv.ui.activity;
import android.app.Activity;
import android.os.Bundle;
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
import com.fongmi.android.tv.R;
import com.jeffmony.downloader.VideoDownloadManager;
import com.jeffmony.downloader.listener.DownloadListener;
import com.jeffmony.downloader.listener.IDownloadInfosCallback;
import com.jeffmony.downloader.model.VideoTaskItem;
import java.util.ArrayList;
import java.util.List;
public class DownloadActivity extends AppCompatActivity {
 private final List<VideoTaskItem> items=new ArrayList<>(); private TaskAdapter adapter;
 public static void start(Activity a){a.startActivity(new android.content.Intent(a,DownloadActivity.class));}
 @Override protected void onCreate(@Nullable Bundle b){super.onCreate(b);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(16,16,16,16);root.setBackgroundColor(0xFF171717);
  TextView title=new TextView(this);title.setText("下载列表");title.setTextSize(22);title.setTextColor(0xFFFFFFFF);title.setPadding(0,0,0,16);root.addView(title,new LinearLayout.LayoutParams(-1,-2));
  Button pauseAll=new Button(this);pauseAll.setText("暂停全部");pauseAll.setOnClickListener(v->VideoDownloadManager.getInstance().pauseAllDownloadTasks());root.addView(pauseAll,new LinearLayout.LayoutParams(-1,-2));
  ListView list=new ListView(this);adapter=new TaskAdapter();list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  list.setOnItemClickListener((p,v,pos,id)->toggle(items.get(pos)));
  list.setOnItemLongClickListener((p,v,pos,id)->{VideoTaskItem item=items.get(pos);new AlertDialog.Builder(this).setTitle("删除下载任务").setMessage("同时删除已下载的本地文件？").setNegativeButton("取消",null).setNeutralButton("仅删除记录",(d,w)->delete(item,false)).setPositiveButton("删除文件和记录",(d,w)->delete(item,true)).show();return true;});
  setContentView(root);VideoDownloadManager.getInstance().setGlobalDownloadListener(listener);VideoDownloadManager.getInstance().fetchDownloadItems(infosCallback);
 }
 private void toggle(VideoTaskItem i){if(i.isRunningTask()||i.isPendingTask())VideoDownloadManager.getInstance().pauseDownloadTask(i.getUrl());else VideoDownloadManager.getInstance().resumeDownload(i.getUrl());}
 private void delete(VideoTaskItem i,boolean file){VideoDownloadManager.getInstance().deleteVideoTask(i,file);items.remove(i);adapter.notifyDataSetChanged();}
 private void update(VideoTaskItem i){runOnUiThread(()->{int n=-1;for(int x=0;x<items.size();x++)if(items.get(x).getUrl().equals(i.getUrl())){n=x;break;}if(n>=0)items.set(n,i);else items.add(0,i);adapter.notifyDataSetChanged();});}
 private final DownloadListener listener=new DownloadListener(){
  @Override public void onDownloadDefault(VideoTaskItem i){update(i);}
  @Override public void onDownloadPending(VideoTaskItem i){update(i);}
  @Override public void onDownloadPrepare(VideoTaskItem i){update(i);}
  @Override public void onDownloadStart(VideoTaskItem i){update(i);}
  @Override public void onDownloadProgress(VideoTaskItem i){update(i);}
  @Override public void onDownloadSpeed(VideoTaskItem i){update(i);}
  @Override public void onDownloadPause(VideoTaskItem i){update(i);}
  @Override public void onDownloadError(VideoTaskItem i){update(i);}
  @Override public void onDownloadSuccess(VideoTaskItem i){update(i);}
 };
 private final IDownloadInfosCallback infosCallback=infos->runOnUiThread(()->{items.clear();items.addAll(infos);adapter.notifyDataSetChanged();});
 @Override protected void onDestroy(){VideoDownloadManager.getInstance().removeDownloadInfosCallback(infosCallback);super.onDestroy();}
 private class TaskAdapter extends BaseAdapter{
  @Override public int getCount(){return items.size();}@Override public Object getItem(int p){return items.get(p);}@Override public long getItemId(int p){return p;}
  @Override public View getView(int p,View cv,ViewGroup parent){Row row;if(cv==null){LinearLayout line=new LinearLayout(DownloadActivity.this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(8,12,8,12);
   ImageView cover=new ImageView(DownloadActivity.this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);line.addView(cover,new LinearLayout.LayoutParams(104,72));
   LinearLayout texts=new LinearLayout(DownloadActivity.this);texts.setOrientation(LinearLayout.VERTICAL);texts.setPadding(12,0,0,0);line.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
   TextView name=new TextView(DownloadActivity.this);name.setTextColor(0xFFFFFFFF);name.setTextSize(16);TextView state=new TextView(DownloadActivity.this);state.setTextColor(0xFFBBBBBB);state.setTextSize(13);TextView progress=new TextView(DownloadActivity.this);progress.setTextColor(0xFFBBBBBB);progress.setTextSize(13);
   texts.addView(name);texts.addView(state);texts.addView(progress);row=new Row(cover,name,state,progress);line.setTag(row);cv=line;
  }else row=(Row)cv.getTag();VideoTaskItem i=items.get(p);row.name.setText(i.getTitle()==null||i.getTitle().isEmpty()?i.getFileName():i.getTitle());row.state.setText(state(i));row.progress.setText(i.getPercentString()+"  "+i.getDownloadSizeString());Glide.with(DownloadActivity.this).load(i.getCoverUrl()).placeholder(R.drawable.ic_action_history).into(row.cover);return cv;}
 }
 private String state(VideoTaskItem i){if(i.isSuccessState())return "已完成："+i.getFilePath();if(i.isErrorState())return "下载失败，点击重试";if(i.isRunningTask())return "下载中，点击暂停";if(i.isPendingTask())return "排队中，点击暂停";return "已暂停，点击继续";}
 private static class Row{final ImageView cover;final TextView name,state,progress;Row(ImageView c,TextView n,TextView s,TextView p){cover=c;name=n;state=s;progress=p;}}
}