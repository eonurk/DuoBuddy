package family.tv.companion;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Api {
    static final ExecutorService IO=Executors.newFixedThreadPool(3);
    public interface Callback {void done(JSONObject value, String error);}
    /** Reported to the controller so it knows whether this build can listen in place. */
    static final int VERSION=9;
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("pairing",Context.MODE_PRIVATE);}
    static boolean paired(Context c){return !prefs(c).getString("server","").isEmpty()&&!prefs(c).getString("token","").isEmpty();}
    static void request(Context c,String path,JSONObject data,Callback callback){
        IO.execute(()->{JSONObject result=null;String error=null;
            try{result=blocking(c,path,data);}catch(Exception e){error=e.getMessage();}
            final JSONObject r=result;final String err=error;
            if(callback!=null)new android.os.Handler(android.os.Looper.getMainLooper()).post(()->callback.done(r,err));
        });
    }
    static JSONObject blocking(Context c,String path,JSONObject data)throws Exception{
        return blocking(c,path,data,null);
    }
    /** A dedicated connection leaves ordinary chat, Stop and presence requests free. */
    static final class StateWatch {
        private final Context context;
        private final ExecutorService worker=Executors.newSingleThreadExecutor();
        private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
        private volatile boolean closed;
        private HttpURLConnection connection;
        StateWatch(Context c){context=c.getApplicationContext();}
        synchronized boolean attach(HttpURLConnection c){if(closed)return false;connection=c;return true;}
        synchronized void detach(HttpURLConnection c){if(connection==c)connection=null;}
        void request(long revision,String session,Callback callback){
            if(closed)return;
            worker.execute(()->{
                JSONObject value=null;String error=null;
                String path="/api/state"+(revision<0?"":"?since="+revision+"&session="+android.net.Uri.encode(session));
                try{value=blocking(context,path,null,this);}catch(Exception e){error=e.getMessage();}
                final JSONObject result=value;final String failure=error;
                if(!closed)main.post(()->{if(!closed)callback.done(result,failure);});
            });
        }
        void close(){HttpURLConnection old;synchronized(this){closed=true;old=connection;connection=null;}if(old!=null)old.disconnect();worker.shutdownNow();}
    }
    private static JSONObject blocking(Context c,String path,JSONObject data,StateWatch watch)throws Exception{
        String server=prefs(c).getString("server","");String token=prefs(c).getString("token","");
        if(server.isEmpty()||token.isEmpty())throw new IOException("Pair this TV from the Mac first.");
        HttpURLConnection con=(HttpURLConnection)new URL(server+path).openConnection();
        try{
            if(watch!=null&&!watch.attach(con))throw new IOException("State connection closed");
            con.setConnectTimeout(4500);con.setReadTimeout(watch==null?10000:25000);con.setRequestProperty("Authorization","Bearer "+token);con.setRequestProperty("X-Buddy-Version",String.valueOf(VERSION));con.setRequestProperty("X-Buddy-Voice",VoiceStore.selected(c).id);
            if(data!=null){con.setRequestMethod("POST");con.setDoOutput(true);con.setRequestProperty("Content-Type","application/json");try(OutputStream out=con.getOutputStream()){out.write(data.toString().getBytes("UTF-8"));}}
            int code=con.getResponseCode();InputStream stream=code<400?con.getInputStream():con.getErrorStream();
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
            try(InputStream input=stream){while((n=input.read(buffer))!=-1)out.write(buffer,0,n);}
            JSONObject r=new JSONObject(out.toString("UTF-8"));if(code>=400)throw new IOException(r.optString("error","Connection failed"));return r;
        }finally{if(watch!=null)watch.detach(con);con.disconnect();}
    }
    static JSONObject json(String...pairs){JSONObject j=new JSONObject();try{for(int i=0;i<pairs.length;i+=2)j.put(pairs[i],pairs[i+1]);}catch(Exception ignored){}return j;}
}
