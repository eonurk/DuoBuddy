package family.tv.companion;

import android.content.*;
import android.content.pm.*;
import android.media.AudioManager;
import android.net.Uri;
import android.view.KeyEvent;
import org.json.*;
import java.util.*;

final class LocalTvTools {
    private final Context context;private final AudioManager audio;
    LocalTvTools(Context c){context=c;audio=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);}
    JSONArray apps(){
        TreeMap<String,JSONObject> found=new TreeMap<>();PackageManager pm=context.getPackageManager();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(category);
            for(ResolveInfo item:pm.queryIntentActivities(intent,0)){
                String pkg=item.activityInfo.packageName;if(pkg.equals(context.getPackageName())||pkg.contains("packageinstaller")||pkg.contains("permissioncontroller"))continue;
                found.put(pkg,TvJson.object("key",pkg,"pkg",pkg,"label",item.loadLabel(pm).toString()));
            }
        }
        ArrayList<JSONObject> list=new ArrayList<>(found.values());Collections.sort(list,(a,b)->a.optString("label").compareToIgnoreCase(b.optString("label")));
        JSONArray result=new JSONArray();for(JSONObject item:list)result.put(item);return result;
    }
    private JSONObject app(String value){
        String query=value.trim().toLowerCase(Locale.ROOT);Map<String,String> aliases=new HashMap<>();aliases.put("youtube","com.google.android.youtube.tv");aliases.put("spotify","com.spotify.tv.android");aliases.put("netflix","com.netflix.ninja");aliases.put("stremio","com.stremio.one");aliases.put("prime video","com.amazon.amazonvideo.livingroom");aliases.put("plex","com.plexapp.android");aliases.put("vlc","org.videolan.vlc");
        JSONArray list=apps();for(int i=0;i<list.length();i++){JSONObject item=list.optJSONObject(i);if(item.optString("pkg").equals(value)||item.optString("label").equalsIgnoreCase(value)||item.optString("pkg").equals(aliases.get(query)))return item;}
        throw new IllegalArgumentException("That app is not in the TV launcher list.");
    }
    JSONObject status(){
        return TvJson.object("foreground",CompanionService.visible?context.getPackageName():TvAccess.foreground(),"musicActive",audio.isMusicActive(),"volume",Math.round(100f*audio.getStreamVolume(AudioManager.STREAM_MUSIC)/Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))),"accessible",TvAccess.enabled(),"screenReading","Accessible text only when TV control is enabled; no ADB or screenshots.");
    }
    boolean playing(){return audio.isMusicActive();}
    JSONObject run(String name,JSONObject args)throws Exception{
        if(name.equals("open_app")){
            JSONObject app=app(LocalData.printable(args.optString("app"),80));PackageManager pm=context.getPackageManager();Intent open=pm.getLeanbackLaunchIntentForPackage(app.optString("pkg"));if(open==null)open=pm.getLaunchIntentForPackage(app.optString("pkg"));if(open==null)throw new IllegalStateException("This app has no launch activity.");
            context.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return TvJson.object("message","Launch requested for "+app.optString("label")+". This does not verify playback.");
        }
        if(name.equals("search_app")){
            JSONObject target=app(LocalData.printable(args.optString("app"),80));String pkg=target.optString("pkg"),q=Uri.encode(LocalData.printable(args.optString("query"),200)),uri;
            if(pkg.equals("com.google.android.youtube.tv"))uri="https://www.youtube.com/results?search_query="+q;
            else if(pkg.equals("com.spotify.tv.android"))uri="spotify:search:"+q;
            else if(pkg.equals("com.netflix.ninja"))uri="nflx://www.netflix.com/search?query="+q;
            else if(pkg.equals("com.amazon.amazonvideo.livingroom"))uri="https://app.primevideo.com/search?phrase="+q;
            else throw new IllegalStateException("This app has no supported standalone search link. Open it and use TV control if available.");
            context.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(uri)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return TvJson.object("message","Search link opened. Search results and playback still need verification.");
        }
        if(name.equals("open_url")){
            String value=LocalData.printable(args.optString("url"),2000);Uri uri=Uri.parse(value);
            if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException("Only HTTPS links are allowed.");
            context.startActivity(new Intent(Intent.ACTION_VIEW,uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return TvJson.object("message","HTTPS link opened; result is not yet verified.");
        }
        if(name.equals("volume")){
            String action=args.optString("action");int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),now=audio.getStreamVolume(AudioManager.STREAM_MUSIC),amount=args.isNull("amount")?2:args.optInt("amount");
            int next;
            if(action.equals("set")){if(amount<0||amount>100)throw new IllegalArgumentException("Volume must be 0–100 percent.");next=Math.round(max*amount/100f);}
            else if(action.equals("up")||action.equals("down")){if(amount<1||amount>8)throw new IllegalArgumentException("Volume steps must be 1–8.");next=now+(action.equals("up")?amount:-amount);}
            else if(action.equals("mute")){if(now>0)Api.prefs(context).edit().putInt("muted_from",now).apply();next=0;}
            else if(action.equals("unmute"))next=Api.prefs(context).getInt("muted_from",Math.max(1,max/3));
            else throw new IllegalArgumentException("Unknown volume action.");
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.max(0,Math.min(max,next)),0);return TvJson.object("message","Volume is "+Math.round(100f*audio.getStreamVolume(AudioManager.STREAM_MUSIC)/Math.max(1,max))+" percent.");
        }
        if(name.equals("media")){
            Map<String,Integer> keys=new HashMap<>();keys.put("play",126);keys.put("pause",127);keys.put("play_pause",85);keys.put("next",87);keys.put("previous",88);keys.put("stop",86);keys.put("rewind",89);keys.put("fast_forward",90);
            Integer key=keys.get(args.optString("command"));if(key==null)throw new IllegalArgumentException("Unknown media command.");
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,key));return TvJson.object("message","Media command sent. Individual apps may ignore it.");
        }
        if(name.equals("tv_status"))return status();
        if(name.equals("read_screen")||name.equals("remote")||name.equals("tap")||name.equals("type_text"))return TvAccess.call(name,args);
        if(name.equals("screensaver")){context.startActivity(new Intent(context,AmbientActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return TvJson.object("message","DuoBuddy screensaver opened.");}
        if(name.equals("wait")){int seconds=args.optInt("seconds");if(seconds<1||seconds>8)throw new IllegalArgumentException("Wait must be 1–8 seconds.");Thread.sleep(seconds*1000L);return TvJson.object("message","Waited.");}
        throw new IllegalArgumentException("Unknown TV tool.");
    }
}
