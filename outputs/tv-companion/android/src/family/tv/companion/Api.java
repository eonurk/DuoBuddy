package family.tv.companion;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The same UI contract now routes entirely inside the APK; no controller connection exists. */
public final class Api {
    static final ExecutorService IO=Executors.newFixedThreadPool(3);
    static final int VERSION=11;
    public interface Callback {void done(JSONObject value,String error);}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("pairing",Context.MODE_PRIVATE);}
    static boolean paired(Context c){return true;}
    static void request(Context c,String path,JSONObject data,Callback callback){
        Context context=c.getApplicationContext();
        IO.execute(()->{JSONObject value=null;String error=null;
            try{value=blocking(context,path,data);}catch(Exception e){error=e.getMessage();}
            final JSONObject result=value;final String failure=error;
            if(callback!=null)new android.os.Handler(android.os.Looper.getMainLooper()).post(()->callback.done(result,failure));
        });
    }
    static JSONObject blocking(Context c,String path,JSONObject data)throws Exception{return LocalController.get(c).route(path,data);}
    static final class StateWatch {
        private final Context context;private final ExecutorService worker=Executors.newSingleThreadExecutor();
        private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());private volatile boolean closed;
        StateWatch(Context c){context=c.getApplicationContext();}
        void request(long revision,String session,Callback callback){
            if(closed)return;worker.execute(()->{JSONObject value=null;String error=null;
                try{value=revision<0?LocalController.get(context).snapshot():LocalController.get(context).awaitState(revision,session);}catch(Exception e){error=e.getMessage();}
                final JSONObject result=value;final String failure=error;if(!closed)main.post(()->{if(!closed)callback.done(result,failure);});
            });
        }
        void close(){closed=true;worker.shutdownNow();}
    }
    static JSONObject json(String...pairs){Object[] values=new Object[pairs.length];System.arraycopy(pairs,0,values,0,pairs.length);return TvJson.object(values);}
}
