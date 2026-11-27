package com.fongmi.android.tv.utils;

import android.text.TextUtils;
import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** NewTV2 TMDB-enriched hero and four poster shelves, TV-home only. */
public final class NewTV2HomeData {
    private static final String BASE="https://rank-json.tmdbapi.cc.cd";
    private static final String PROXY="https://t.tmdbapi.cc.cd";
    private static final String OFFICIAL="https://image.tmdb.org";
    private static final String HERO=BASE+"/rank/latest";
    private static final String[] BOARDS={BASE+"/rank/latest/movie",BASE+"/rank/latest/series",BASE+"/trending/movie",BASE+"/trending/tv"};
    private static final long TTL=12L*60*60*1000;
    private static final ExecutorService EXEC=Executors.newFixedThreadPool(3,r->{Thread t=new Thread(r,"NewTV2HomeData");t.setDaemon(true);return t;});
    private NewTV2HomeData(){}
    public interface HeroCallback{void onResult(List<HeroItem> items);}
    public interface BoardCallback{void onResult(List<BoardItem> items);}
    public static final class HeroItem{
        private final String title,description,year,genre,rating,backdrop,logo,poster;
        HeroItem(String t,String d,String y,String g,String r,String b,String l,String p){title=t;description=d;year=y;genre=g;rating=r;backdrop=b;logo=l;poster=p;}
        public String getTitle(){return title;}public String getDescription(){return description;}public String getYear(){return year;}public String getGenre(){return genre;}public String getRating(){return rating;}public String getBackdrop(){return backdrop;}public String getLogo(){return logo;}public String getPoster(){return poster;}
    }
    public static final class BoardItem{
        private final String title,poster,rating,year;
        BoardItem(String t,String p,String r,String y){title=t;poster=p;rating=r;year=y;}
        public String getTitle(){return title;}public String getPoster(){return poster;}public String getRating(){return rating;}public String getYear(){return year;}
    }
    public static String proxyImage(String path,String size){return image(PROXY,size,path);}
    public static String officialImage(String path,String size){return image(OFFICIAL,size,path);}
    private static String image(String base,String size,String path){if(TextUtils.isEmpty(path))return "";if(path.startsWith("http://")||path.startsWith("https://"))return path;return base+"/t/p/"+size+(path.startsWith("/")?path:"/"+path);}
    public static void loadHero(HeroCallback cb){
        File f=cache("hero");String cached=read(f);
        if(!cached.isEmpty()){List<HeroItem> list=parseHero(cached);if(!list.isEmpty())App.post(()->cb.onResult(list));if(System.currentTimeMillis()-f.lastModified()<TTL)return;}
        EXEC.execute(()->{String json=fetch(HERO);if(json.isEmpty())return;List<HeroItem> list=parseHero(json);if(list.isEmpty())return;write(f,json);App.post(()->cb.onResult(list));});
    }
    public static void loadBoard(int board,BoardCallback cb){
        if(board<0||board>=BOARDS.length){App.post(()->cb.onResult(Collections.emptyList()));return;}
        File f=cache("board_"+board);String cached=read(f);
        if(!cached.isEmpty()){List<BoardItem> list=parseBoard(cached,board);if(!list.isEmpty())App.post(()->cb.onResult(list));if(System.currentTimeMillis()-f.lastModified()<TTL)return;}
        EXEC.execute(()->{String json=fetch(BOARDS[board]);if(json.isEmpty())return;List<BoardItem> list=parseBoard(json,board);if(list.isEmpty())return;write(f,json);App.post(()->cb.onResult(list));});
    }
    private static List<HeroItem> parseHero(String raw){
        try{
            JsonObject root=JsonParser.parseString(raw).getAsJsonObject();
            JsonObject c=root.has("categories")&&root.get("categories").isJsonObject()?root.getAsJsonObject("categories"):root;
            List<HeroItem> series=parseHeroItems(c.getAsJsonObject("series")),movie=parseHeroItems(c.getAsJsonObject("movie")),out=new ArrayList<>(12);
            for(int i=0;out.size()<12&&(i<series.size()||i<movie.size());i+=2){for(int n=0;n<2&&i+n<series.size()&&out.size()<12;n++)out.add(series.get(i+n));for(int n=0;n<2&&i+n<movie.size()&&out.size()<12;n++)out.add(movie.get(i+n));}
            if(out.isEmpty())out.addAll(parseHeroItems(root));return out.size()>12?new ArrayList<>(out.subList(0,12)):out;
        }catch(Exception e){return Collections.emptyList();}
    }
    private static List<HeroItem> parseHeroItems(JsonObject cat){
        if(cat==null||!cat.has("items")||!cat.get("items").isJsonArray())return Collections.emptyList();
        List<HeroItem> out=new ArrayList<>();for(JsonElement el:cat.getAsJsonArray("items")){if(el==null||!el.isJsonObject())continue;JsonObject x=el.getAsJsonObject();String title=str(x,"title"),backdrop=str(x,"backdropPath"),poster=str(x,"posterPath");if(title.isEmpty()||(backdrop.isEmpty()&&poster.isEmpty()))continue;if(backdrop.isEmpty())backdrop=poster;String date=str(x,"releaseDate");out.add(new HeroItem(title,str(x,"description"),date.length()>=4?date.substring(0,4):"",str(x,"genreTitle"),str(x,"rating"),backdrop,str(x,"logoPath"),poster));}return out;
    }
    private static List<BoardItem> parseBoard(String raw,int board){
        try{JsonObject root=JsonParser.parseString(raw).getAsJsonObject();JsonArray a=root.has("items")&&root.get("items").isJsonArray()?root.getAsJsonArray("items"):root.has("results")&&root.get("results").isJsonArray()?root.getAsJsonArray("results"):null;if(a==null)return Collections.emptyList();List<BoardItem> out=new ArrayList<>();
            for(JsonElement el:a){if(el==null||!el.isJsonObject())continue;JsonObject x=el.getAsJsonObject();String title=str(x,"title");if(title.isEmpty())title=str(x,"name");String poster=str(x,board<2?"posterPath":"poster_path");if(poster.isEmpty()&&board<2)poster=str(x,"poster_path");if(title.isEmpty()||poster.isEmpty())continue;if(!poster.startsWith("http://")&&!poster.startsWith("https://"))poster=proxyImage(poster,"w500");String rating=str(x,board<2?"rating":"vote_average"),date=str(x,board<2?"releaseDate":"release_date");if(date.isEmpty())date=str(x,"first_air_date");out.add(new BoardItem(title.trim(),poster,rating,date.length()>=4?date.substring(0,4):""));if(out.size()>=60)break;}return out;
        }catch(Exception e){return Collections.emptyList();}
    }
    private static String str(JsonObject x,String key){if(x==null||!x.has(key)||x.get(key).isJsonNull())return "";try{return x.get(key).getAsString().trim();}catch(Exception e){return "";}}
    private static String fetch(String url){try(okhttp3.Response r=OkHttp.newCall(url).execute()){return r.isSuccessful()&&r.body()!=null?r.body().string():"";}catch(Exception e){return "";}}
    private static File cache(String n){return new File(App.get().getFilesDir(),"newtv2_"+n+".json");}
    private static String read(File f){if(!f.exists()||f.length()==0)return "";try(FileInputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}catch(Exception e){return "";}}
    private static void write(File f,String json){try{File tmp=new File(f.getParentFile(),f.getName()+".tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(json.getBytes(StandardCharsets.UTF_8));}if(f.exists()&&!f.delete())return;if(!tmp.renameTo(f))return;f.setLastModified(System.currentTimeMillis());}catch(Exception ignored){}}
}
