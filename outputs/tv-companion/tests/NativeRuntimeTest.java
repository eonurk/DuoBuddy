package family.tv.companion;
import android.app.Instrumentation;
import android.content.*;
import android.os.*;
import org.json.*;

/** Separate instrumentation APK: never packaged in the release application. */
public final class NativeRuntimeTest extends Instrumentation {
 private Bundle args;
 public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
 private static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);}
 public void onStart(){Bundle result=new Bundle();try{
  Context target=getTargetContext();String mode=args.getString("mode","storage");
  if(mode.equals("storage")){
   Context isolated=new ContextWrapper(target){public SharedPreferences getSharedPreferences(String n,int m){return super.getSharedPreferences("instrumentation-"+n,m);}};
   isolated.getSharedPreferences("duobuddy-memory",0).edit().clear().commit();
   KeyVault vault=new KeyVault(isolated);vault.save("test-provider","sample-not-a-real-key");check(vault.read("test-provider").equals("sample-not-a-real-key"),"encrypted round trip");
   String raw=isolated.getSharedPreferences("provider-secrets",0).getString("test-provider","");check(!raw.contains("sample"),"no plaintext secret");vault.remove("test-provider");check(!vault.has("test-provider"),"key removal");
   LocalData data=new LocalData(isolated);data.profile(TvJson.object("action","switch","name","Test Person"));data.memory(TvJson.object("action","remember","note","Likes subtitles","scope","person"));
   LocalData restored=new LocalData(isolated);check(restored.person().equals("Test Person"),"profile persistence");check(restored.notes().toString().contains("Likes subtitles"),"memory persistence");
   restored.profile(TvJson.object("action","leave"));check(!restored.notes().toString().contains("Likes subtitles"),"person isolation");
   JSONObject timer=restored.timer(TvJson.object("action","set","seconds",60,"label","Test"));int id=timer.getInt("id");
   check(new LocalData(isolated).timers().length()==1,"timer persistence");restored.timer(TvJson.object("action","cancel","id",id));check(new LocalData(isolated).timers().length()==0,"timer cancellation persistence");
   isolated.getSharedPreferences("duobuddy-memory",0).edit().clear().commit();result.putString("result","Encrypted keys, profiles, memory isolation, persistent timers and cancellation passed");
  }else if(mode.equals("chat")){
   LocalController runtime=LocalController.get(target);String text=args.getString("text","Say hello in one short sentence");String previous=runtime.snapshot().getString("replyId");
   target.startForegroundService(new Intent(target,CompanionService.class));runtime.route("/api/chat",TvJson.object("text",text));long end=SystemClock.elapsedRealtime()+90000;JSONObject state;
   do{Thread.sleep(200);state=runtime.snapshot();}while((state.optBoolean("busy")||state.optString("replyId").equals(previous))&&SystemClock.elapsedRealtime()<end);
   check(!state.optBoolean("busy"),"request finished");check(state.optString("error").isEmpty(),state.optString("error"));check(!state.optString("reply").isEmpty(),"reply received");
   result.putString("reply",state.optString("reply"));result.putBoolean("standalone",state.optBoolean("standalone"));Thread.sleep(12000);
  }else if(mode.equals("setup")){
   target.startActivity(new Intent(target,KeySetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(3000);result.putString("result","Opened native setup");
  }
  finish(-1,result);
 }catch(Throwable e){result.putString("failure",e.toString());finish(0,result);}}
}
