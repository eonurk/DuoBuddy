package family.tv.companion;

import android.app.*;
import android.content.*;
import org.json.*;
import java.util.*;

/** App-private household state and alarm-backed timers. No network or API credentials. */
final class LocalData {
    private final Context context;
    private final SharedPreferences prefs;
    private JSONObject data;
    LocalData(Context c){
        context=c.getApplicationContext();prefs=context.getSharedPreferences("duobuddy-memory",Context.MODE_PRIVATE);
        try{data=new JSONObject(prefs.getString("data","{}"));}catch(JSONException e){throw new IllegalStateException("Saved memory is unreadable; use Settings to reset it.");}
    }
    private void save(){if(!prefs.edit().putString("data",data.toString()).commit())throw new IllegalStateException("Could not save household data.");}
    private JSONObject people(){JSONObject p=data.optJSONObject("people");if(p==null){p=new JSONObject();put(data,"people",p);}return p;}
    private JSONArray household(){JSONArray a=data.optJSONArray("notes");if(a==null){a=new JSONArray();put(data,"notes",a);}return a;}
    static String printable(String text,int max){if(text==null||text.trim().isEmpty()||text.length()>max||text.matches("(?s).*[\\p{Cntrl}].*"))throw new IllegalArgumentException("Use 1–"+max+" printable characters.");return text.trim();}
    static void put(JSONObject o,String key,Object value){try{o.put(key,value);}catch(JSONException e){throw new IllegalArgumentException(e);}}
    synchronized String person(){return data.optString("person","");}
    synchronized JSONArray notes(){JSONArray result=new JSONArray();String who=person();JSONObject p=people().optJSONObject(who);
        if(p!=null){JSONArray n=p.optJSONArray("notes");if(n!=null)for(int i=0;i<n.length();i++)result.put(who+": "+n.optString(i));}
        JSONArray h=household();for(int i=0;i<h.length();i++)result.put("Household: "+h.optString(i));return result;
    }
    synchronized JSONObject memory(JSONObject args){
        String action=args.optString("action"),scope=args.isNull("scope")?"":args.optString("scope");
        if(!scope.isEmpty()&&!scope.equals("person")&&!scope.equals("household"))throw new IllegalArgumentException("Unknown memory scope.");
        boolean personal=scope.equals("person")||scope.isEmpty()&&!person().isEmpty();
        if(personal&&person().isEmpty())throw new IllegalArgumentException("Tell me your name before saving a personal note.");
        JSONArray list=household();JSONObject owner=data;
        if(personal){owner=people().optJSONObject(person());list=owner.optJSONArray("notes");if(list==null){list=new JSONArray();put(owner,"notes",list);}}
        if(action.equals("list"))return TvJson.object("notes",notes(),"person",person());
        String note=printable(args.optString("note"),200);
        if(action.equals("remember")){
            if(list.length()>=100)throw new IllegalStateException("This note list is full. Forget a note first.");
            boolean exists=false;for(int i=0;i<list.length();i++)if(note.equalsIgnoreCase(list.optString(i)))exists=true;
            if(!exists)list.put(note);save();return TvJson.object("message","Remembered for "+(personal?person():"the household")+": "+note);
        }
        if(action.equals("forget")){
            JSONArray kept=new JSONArray();int count=0;for(int i=0;i<list.length();i++){if(list.optString(i).toLowerCase(Locale.ROOT).contains(note.toLowerCase(Locale.ROOT)))count++;else kept.put(list.optString(i));}
            put(owner,"notes",kept);save();return TvJson.object("message","Forgot "+count+" matching note(s).");
        }
        throw new IllegalArgumentException("Unknown memory action.");
    }
    synchronized JSONObject profile(JSONObject args){
        String action=args.optString("action");
        if(action.equals("leave")){put(data,"person","");save();return TvJson.object("message","Back to the household.");}
        if(action.equals("list"))return TvJson.object("people",people().names()==null?new JSONArray():people().names(),"person",person());
        String name=printable(args.isNull("name")?person():args.optString("name"),30),found=name;Iterator<String> names=people().keys();
        while(names.hasNext()){String existing=names.next();if(existing.equalsIgnoreCase(name)){found=existing;break;}}
        if(!people().has(found)){if(people().length()>=30)throw new IllegalStateException("The household profile list is full.");put(people(),found,TvJson.object("notes",new JSONArray()));}
        if(action.equals("switch")){put(data,"person",found);save();return TvJson.object("message",found+" is now at the TV.");}
        if(action.equals("birthday")){
            String birthday=args.optString("birthday");if(!birthday.matches("(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])"))throw new IllegalArgumentException("Use a birthday as MM-DD.");
            Calendar date=Calendar.getInstance();date.setLenient(false);date.set(2000,Integer.parseInt(birthday.substring(0,2))-1,Integer.parseInt(birthday.substring(3)));date.getTime();
            put(people().optJSONObject(found),"birthday",birthday);save();return TvJson.object("message","Saved "+found+"’s birthday.");
        }
        throw new IllegalArgumentException("Unknown profile action.");
    }
    private JSONArray rawTimers(){JSONArray a=data.optJSONArray("timers");if(a==null){a=new JSONArray();put(data,"timers",a);}return a;}
    synchronized JSONArray timers(){
        JSONArray list=new JSONArray();for(int i=0;i<rawTimers().length();i++){
            JSONObject t=TvJson.copy(rawTimers().optJSONObject(i));put(t,"secondsLeft",Math.max(0,(t.optLong("dueAt")-System.currentTimeMillis()+999)/1000));list.put(t);
        }return list;
    }
    private PendingIntent alarmIntent(int id){return PendingIntent.getBroadcast(context,id,new Intent(context,LocalAlarmReceiver.class).setAction("duobuddy.timer").putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private void schedule(JSONObject timer){
        AlarmManager alarms=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);long due=Math.max(System.currentTimeMillis()+500,timer.optLong("dueAt"));
        try{alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,alarmIntent(timer.optInt("id")));}
        catch(SecurityException denied){alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,alarmIntent(timer.optInt("id")));}
    }
    synchronized void restoreAlarms(){for(int i=0;i<rawTimers().length();i++)schedule(rawTimers().optJSONObject(i));}
    synchronized JSONObject timer(JSONObject args){
        String action=args.optString("action");if(action.equals("list"))return TvJson.object("timers",timers());
        if(action.equals("set")){
            int seconds=args.optInt("seconds");if(seconds<1||seconds>86400)throw new IllegalArgumentException("Timer must be 1 second to 24 hours.");
            if(rawTimers().length()>=30)throw new IllegalStateException("Too many timers are running.");
            String label=args.isNull("label")?"":args.optString("label");if(!label.isEmpty())label=printable(label,100);
            int id=data.optInt("nextTimer",1);put(data,"nextTimer",id+1);
            JSONObject timer=TvJson.object("id",id,"dueAt",System.currentTimeMillis()+seconds*1000L,"label",label);rawTimers().put(timer);save();schedule(timer);
            return TvJson.object("message","Timer "+id+" set for "+seconds+" seconds"+(label.isEmpty()?"":" ("+label+")")+".","id",id);
        }
        if(action.equals("cancel")){JSONObject removed=removeTimer(args.optInt("id"));if(removed==null)throw new IllegalArgumentException("That timer is not running.");return TvJson.object("message","Cancelled timer "+args.optInt("id")+".");}
        throw new IllegalArgumentException("Unknown timer action.");
    }
    synchronized JSONObject removeTimer(int id){
        JSONArray kept=new JSONArray();JSONObject removed=null;for(int i=0;i<rawTimers().length();i++){JSONObject t=rawTimers().optJSONObject(i);if(t.optInt("id")==id)removed=t;else kept.put(t);}
        if(removed!=null){put(data,"timers",kept);save();((AlarmManager)context.getSystemService(Context.ALARM_SERVICE)).cancel(alarmIntent(id));}return removed;
    }
    synchronized void resetNotes(){put(data,"notes",new JSONArray());put(data,"people",new JSONObject());put(data,"person","");save();}
}
