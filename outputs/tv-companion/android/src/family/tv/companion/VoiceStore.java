package family.tv.companion;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** Small built-in voice catalogue; optional model downloads remain on the TV. */
final class VoiceStore {
    static final String DEFAULT="en_US-amy-low",SYSTEM="system";
    static final class Voice {
        final String id,label,description,url,sha;final long bytes;final int rate;final boolean bundled;
        Voice(String id,String label,String description,int rate,long bytes,String url,String sha,boolean bundled){this.id=id;this.label=label;this.description=description;this.rate=rate;this.bytes=bytes;this.url=url;this.sha=sha;this.bundled=bundled;}
        String size(){return Math.round(bytes/1000000.0)+" MB";}
    }
    interface Listener {void progress(long bytes,long total);void ready();void failed(String message);}
    static final class Download {
        final AtomicBoolean cancelled=new AtomicBoolean();volatile HttpURLConnection connection;
        void cancel(){cancelled.set(true);HttpURLConnection c=connection;if(c!=null)c.disconnect();}
    }
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static List<Voice> voices;
    private static boolean downloading;
    static synchronized List<Voice> list(Context c){
        if(voices!=null)return voices;
        ArrayList<Voice> result=new ArrayList<>();
        result.add(new Voice(SYSTEM,"TV voice","Fastest · the TV's own Google speech",0,0,"","",true));
        result.add(new Voice(DEFAULT,"Amy Low","Buddy's own voice · included",16000,63104657,"","",true));
        try(InputStream in=c.getAssets().open("voices/catalog.json")){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);
            JSONArray all=new JSONArray(bytes.toString("UTF-8"));
            for(int i=0;i<all.length();i++){
                JSONObject v=all.getJSONObject(i);
                result.add(new Voice(v.getString("id"),v.getString("label"),v.getString("description"),v.getInt("sampleRate"),v.getLong("bytes"),v.getString("url"),v.getString("sha256"),false));
            }
        }catch(Exception e){android.util.Log.e("BuddyVoice","Voice catalogue unavailable",e);}
        voices=Collections.unmodifiableList(result);return voices;
    }
    static Voice find(Context c,String id){for(Voice v:list(c))if(v.id.equals(id))return v;return list(c).get(0);}
    static Voice selected(Context c){
        // Piper runs slower than real time on weak TVs, so existing installs move to the TV voice once; Piper stays selectable.
        android.content.SharedPreferences prefs=Api.prefs(c);if(!prefs.getBoolean("voice_tv_default",false))prefs.edit().putString("voice_id",SYSTEM).putBoolean("voice_tv_default",true).apply();
        Voice v=find(c,prefs.getString("voice_id",SYSTEM));return installed(c,v)?v:list(c).get(0);
    }
    static boolean system(Context c){return selected(c).id.equals(SYSTEM);}
    /** The Piper model to load: the selected voice, or Amy Low while the TV voice is selected. */
    static Voice piper(Context c){Voice v=selected(c);return v.id.equals(SYSTEM)?find(c,DEFAULT):v;}
    static File folder(Context c,Voice v){return new File(c.getFilesDir(),"voices/"+v.id);}
    static File model(Context c,Voice v){return new File(folder(c,v),"model.onnx");}
    static boolean installed(Context c,Voice v){return v.bundled||(model(c,v).length()==v.bytes&&new File(folder(c,v),"tokens.txt").isFile());}
    static void select(Context c,Voice v){if(!installed(c,v))throw new IllegalStateException("Download this voice first");Api.prefs(c).edit().putString("voice_id",v.id).apply();}
    static void remove(Context c,Voice v){
        if(v.bundled)return;
        if(selected(c).id.equals(v.id))select(c,list(c).get(0));
        File[] files=folder(c,v).listFiles();if(files!=null)for(File f:files)f.delete();folder(c,v).delete();
    }
    static Download install(Context context,Voice voice,Listener listener){
        Context c=context.getApplicationContext();Download request=new Download();
        synchronized(VoiceStore.class){if(downloading){MAIN.post(()->listener.failed("Another voice is downloading. Please wait."));return request;}downloading=true;}
        IO.execute(()->{
            HttpURLConnection connection=null;
            try{
                if(c.getFilesDir().getUsableSpace()<voice.bytes+20_000_000)throw new IOException("Not enough space for this voice. Remove a downloaded voice first.");
                URL url=new URL(voice.url);
                for(int redirects=0;;redirects++){
                    if(request.cancelled.get())throw new IOException("Download cancelled");
                    if(!"https".equals(url.getProtocol()))throw new IOException("Voice downloads require HTTPS");
                    connection=(HttpURLConnection)url.openConnection();request.connection=connection;
                    connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setInstanceFollowRedirects(false);
                    int status=connection.getResponseCode();
                    if(status>=300&&status<400&&redirects<6){String location=connection.getHeaderField("Location");connection.disconnect();if(location==null)throw new IOException("Voice download redirect was empty");url=new URL(url,location);continue;}
                    if(status!=200)throw new IOException("Voice download failed (HTTP "+status+")");break;
                }
                File dir=folder(c,voice);if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create voice storage");
                // Tokens and provenance are tiny, immutable assets shipped with the catalogue.
                for(String name:new String[]{"tokens.txt","MODEL_CARD",voice.id+".onnx.json"})try(InputStream in=c.getAssets().open("voices/"+voice.id+"/"+name);OutputStream out=new FileOutputStream(new File(dir,name))){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
                final long[] last={0};final long deadline=android.os.SystemClock.elapsedRealtime()+600000;
                try(InputStream in=connection.getInputStream()){
                    VoiceFile.install(in,model(c,voice),voice.bytes,voice.sha,(bytes,total)->{
                        if(request.cancelled.get())throw new IOException("Download cancelled");
                        long now=android.os.SystemClock.elapsedRealtime();if(now>deadline)throw new IOException("Voice download timed out. Please try again.");
                        if(now-last[0]>250||bytes==total){last[0]=now;MAIN.post(()->{if(!request.cancelled.get())listener.progress(bytes,total);});}
                    });
                }
                if(request.cancelled.get())throw new IOException("Download cancelled");
                MAIN.post(()->{if(!request.cancelled.get())listener.ready();});
            }catch(Exception e){String message=request.cancelled.get()?"Download cancelled":e.getMessage();MAIN.post(()->listener.failed(message));}
            finally{if(connection!=null)connection.disconnect();request.connection=null;synchronized(VoiceStore.class){downloading=false;}}
        });
        return request;
    }
}
