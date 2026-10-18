package family.tv.companion;

import org.json.*;
import java.io.*;

final class TvJson {
    static JSONObject object(Object... pairs){
        JSONObject o=new JSONObject();
        try{for(int i=0;i<pairs.length;i+=2)o.put((String)pairs[i],pairs[i+1]==null?JSONObject.NULL:pairs[i+1]);}
        catch(JSONException e){throw new IllegalArgumentException(e);}
        return o;
    }
    static JSONArray array(Object... values){JSONArray a=new JSONArray();for(Object value:values)a.put(value);return a;}
    static String read(InputStream input,int limit)throws IOException{
        if(input==null)return "";
        try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("Response is too large");out.write(b,0,n);}return out.toString("UTF-8");
        }
    }
    static JSONObject copy(JSONObject o){try{return new JSONObject(o.toString());}catch(JSONException e){throw new IllegalArgumentException(e);}}
}
