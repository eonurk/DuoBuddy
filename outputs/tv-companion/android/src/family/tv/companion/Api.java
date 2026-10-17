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
    static final int VERSION=6;
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
        String server=prefs(c).getString("server","");String token=prefs(c).getString("token","");
        if(server.isEmpty()||token.isEmpty())throw new IOException("Pair this TV from the Mac first.");
        HttpURLConnection con=(HttpURLConnection)new URL(server+path).openConnection();
        try{
            con.setConnectTimeout(4500);con.setReadTimeout(10000);con.setRequestProperty("Authorization","Bearer "+token);con.setRequestProperty("X-Buddy-Version",String.valueOf(VERSION));
            if(data!=null){con.setRequestMethod("POST");con.setDoOutput(true);con.setRequestProperty("Content-Type","application/json");try(OutputStream out=con.getOutputStream()){out.write(data.toString().getBytes("UTF-8"));}}
            int code=con.getResponseCode();InputStream stream=code<400?con.getInputStream():con.getErrorStream();
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
            try(InputStream input=stream){while((n=input.read(buffer))!=-1)out.write(buffer,0,n);}
            JSONObject r=new JSONObject(out.toString("UTF-8"));if(code>=400)throw new IOException(r.optString("error","Connection failed"));return r;
        }finally{con.disconnect();}
    }
    static JSONObject json(String...pairs){JSONObject j=new JSONObject();try{for(int i=0;i<pairs.length;i+=2)j.put(pairs[i],pairs[i+1]);}catch(Exception ignored){}return j;}
}
