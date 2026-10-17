package family.tv.companion;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.media.MediaPlayer;
import android.media.AudioManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.view.*;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Buddy's always-on presence: polls the Mac controller, plays its speech, owns the
 * microphone and draws the little companion with live captions over any app.
 */
public final class CompanionService extends Service {
    public static final String LISTEN="family.tv.companion.LISTEN";
    public static volatile JSONObject state=new JSONObject();
    public static volatile boolean visible=false,speaking=false,listening=false;
    public static volatile String heard="",localError="";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WindowManager wm;private LinearLayout bubbleRoot;private TextView caption;private FaceView bubble;
    private MediaPlayer player;private AudioManager audioManager;private SpeechRecognizer recognizer;
    private boolean polling=false,destroyed=false,autoListen=false,followUpListen=false;
    private String lastReply=null,audioError="",captionReply="";
    private long captionUntil=0;
    // A reaction to what's on: Buddy pops in for a few seconds, even over a video.
    private int lastReaction=-1;private long reactionUntil=0;private JSONObject reaction=null;
    public IBinder onBind(Intent i){return null;}
    public void onCreate(){
        super.onCreate();wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        String channel="buddy";NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel(channel,"TV Companion",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(12,new Notification.Builder(this,channel).setContentTitle("Buddy is ready").setContentText("Press the remote’s microphone button to talk. The microphone listens only when requested.").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(open).build());
        audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);
        handler.post(poll);
    }
    public int onStartCommand(Intent intent,int flags,int id){
        String action=intent==null?null:intent.getAction();
        if("stop_speaking".equals(action)){autoListen=false;stopAudio();}
        if("voice_test".equals(action))Api.request(this,"/api/voice-test",new JSONObject(),null);
        if(LISTEN.equals(action))startListening(false);
        refreshBubble();return START_STICKY;
    }

    // ---- speech output ----
    private void stopAudio(){if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}speaking=false;if(audioManager!=null)audioManager.abandonAudioFocus(null);broadcast();}
    private void playAudio(String path){
        stopAudio();audioError="";if(path.isEmpty())return;
        if(!path.matches("/api/audio/[a-f0-9]{32}\\.wav")){audioError="Invalid Piper audio path.";return;}
        try{
            player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            HashMap<String,String> headers=new HashMap<>();headers.put("Authorization","Bearer "+Api.prefs(this).getString("token",""));
            player.setDataSource(this,Uri.parse(Api.prefs(this).getString("server","")+path),headers);
            player.setOnPreparedListener(p->{if(destroyed){stopAudio();return;}audioManager.requestAudioFocus(null,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);p.start();speaking=true;android.util.Log.i("BuddyVoice","Piper audio started");broadcast();});
            player.setOnCompletionListener(p->{android.util.Log.i("BuddyVoice","Piper audio completed");stopAudio();finishCaption(autoListen?4000:-1);if(autoListen){autoListen=false;handler.postDelayed(()->startListening(true),350);}});
            player.setOnErrorListener((p,what,extra)->{audioError="Piper audio could not play ("+what+", "+extra+").";android.util.Log.e("BuddyVoice",audioError);autoListen=false;stopAudio();finishCaption(-1);return true;});
            player.prepareAsync();
        }catch(Exception e){audioError="Piper playback: "+e.getMessage();android.util.Log.e("BuddyVoice",audioError);stopAudio();}
    }

    // ---- speech input ----
    /** Listens over whatever is on screen; falls back to Buddy's full screen when it cannot. */
    private void startListening(boolean followUp){
        if(destroyed)return;
        if(listening&&!followUp&&recognizer!=null){recognizer.stopListening();return;} // a second press ends the utterance
        boolean allowed=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED&&SpeechRecognizer.isRecognitionAvailable(this);
        if(!allowed||(!visible&&!Settings.canDrawOverlays(this))){if(!followUp)startActivity(new Intent(this,MainActivity.class).setAction(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;}
        autoListen=false;followUpListen=followUp;stopAudio();localError="";heard="";
        if(recognizer==null){recognizer=SpeechRecognizer.createSpeechRecognizer(this);recognizer.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle b){android.util.Log.i("BuddyVoice","Speech input ready");listening=true;presence("listening");changed();}
            public void onBeginningOfSpeech(){}public void onRmsChanged(float r){}public void onBufferReceived(byte[] b){}public void onEvent(int e,Bundle b){}
            public void onEndOfSpeech(){}
            public void onError(int e){
                android.util.Log.i("BuddyVoice","Speech error "+e);listening=false;presence("idle");
                boolean quiet=followUpListen&&(e==SpeechRecognizer.ERROR_NO_MATCH||e==SpeechRecognizer.ERROR_SPEECH_TIMEOUT);
                localError=quiet?"":e==SpeechRecognizer.ERROR_NO_MATCH||e==SpeechRecognizer.ERROR_SPEECH_TIMEOUT?"I didn’t catch that. Try again.":"Microphone unavailable ("+e+"). You can type a request in Buddy.";
                if(!localError.isEmpty())captionUntil=SystemClock.uptimeMillis()+6000;
                heard="";changed();
            }
            public void onResults(Bundle b){
                android.util.Log.i("BuddyVoice","Speech transcription received");listening=false;
                ArrayList<String> results=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(results!=null&&!results.isEmpty()&&!results.get(0).trim().isEmpty())sendChat(results.get(0).trim());else{presence("idle");heard="";changed();}
            }
            public void onPartialResults(Bundle b){ArrayList<String> r=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(r!=null&&!r.isEmpty()){heard=r.get(0);changed();}}
        });}
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US");i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
        recognizer.cancel();recognizer.startListening(i);
    }
    private void sendChat(String text){
        stopAudio();heard="";localError="";
        Api.request(this,"/api/chat",Api.json("text",text,"followUp",followUpListen?"1":"0"),(r,e)->{if(e!=null){localError=e;captionUntil=SystemClock.uptimeMillis()+6000;}changed();});
        changed();
    }
    private void presence(String phase){Api.request(this,"/api/presence",Api.json("phase",phase),null);}
    private void changed(){broadcast();refreshBubble();}
    private void broadcast(){Intent i=new Intent("family.tv.companion.STATE");i.setPackage(getPackageName());sendBroadcast(i);handler.post(()->{if(bubble!=null){bubble.phase=effectivePhase();bubble.talking=speaking;}});}
    private String effectivePhase(){return listening?"listening":state.optString("phase","idle");}

    // ---- controller polling ----
    private final Runnable poll=new Runnable(){public void run(){
        if(destroyed)return;
        if(!Api.paired(CompanionService.this)){handler.postDelayed(this,2000);return;}
        if(polling){handler.postDelayed(this,1200);return;}polling=true;
        Api.request(CompanionService.this,"/api/state",null,(data,error)->{
            polling=false;if(destroyed)return;
            if(data!=null){
                state=data;String id=data.optString("replyId","");
                if(lastReply==null)lastReply=id;
                else if(!id.equals(lastReply)){
                    lastReply=id;boolean finalReply="idle".equals(data.optString("phase",""))&&!data.optString("reply","").isEmpty();
                    autoListen=finalReply&&data.optBoolean("followUp",false)&&!data.optString("audioPath","").isEmpty();
                    // The caption stays while Buddy speaks and leaves shortly after (see finishCaption);
                    // with no audio to wait for, it shows for a few seconds of reading time.
                    if(finalReply){captionReply=data.optString("reply","");captionUntil=SystemClock.uptimeMillis()+(data.optString("audioPath","").isEmpty()?Math.min(9000,3000+captionReply.length()*50L):30000);}
                    playAudio(data.optString("audioPath",""));
                }
                JSONObject r=data.optJSONObject("reaction");int rid=r==null?0:r.optInt("id",0);
                // The first poll only records the latest reaction, so a restart doesn't replay it.
                if(lastReaction<0)lastReaction=rid;
                else if(rid!=lastReaction){lastReaction=rid;reaction=r;long ms=Math.max(1500,Math.min(10000,r.optLong("ms",4500)));reactionUntil=SystemClock.uptimeMillis()+ms;handler.postDelayed(()->refreshBubble(),ms+80);}
                if(!audioError.isEmpty())try{state.put("voiceError",audioError);}catch(Exception ignored){}
                broadcast();
            }else{try{state.put("status","Mac connection unavailable");state.put("phase","idle");state.put("error",error);}catch(Exception ignored){}broadcast();}
            refreshBubble();handler.postDelayed(poll,1300);
        });
    }};

    // ---- overlay ----
    private String captionText(){
        long now=SystemClock.uptimeMillis();String phase=state.optString("phase","idle");
        if(listening)return heard.isEmpty()?"Listening…":heard;
        if(!localError.isEmpty()&&now<captionUntil)return localError;
        if(phase.equals("thinking")||phase.equals("acting")){String user=state.optString("user","");return phase.equals("acting")?"On it…":user.isEmpty()?"Thinking…":"“"+user+"”";}
        if(now<captionUntil&&!captionReply.isEmpty())return captionReply;
        if(reacting())return reaction.optString("caption","");
        return "";
    }
    private boolean reacting(){return reaction!=null&&SystemClock.uptimeMillis()<reactionUntil;}
    private boolean busyPhase(){String phase=state.optString("phase","idle");return listening||speaking||phase.equals("thinking")||phase.equals("acting");}
    private static int hex(String c,int fallback){try{return android.graphics.Color.parseColor(c);}catch(Exception e){return fallback;}}
    /** Gives the bubble the reaction's pose, outfit and scarf colours while it lasts. */
    private void dress(FaceView f){
        boolean on=reacting()&&!busyPhase();
        f.setMood(on?reaction.optString("mood",""):"");f.dressUp=on?reaction.optJSONObject("outfit"):null;
        JSONObject sc=on?reaction.optJSONObject("scarf"):null;if(sc!=null){f.scarfMain=hex(sc.optString("primary"),0xffe11d48);f.scarfStripe=hex(sc.optString("secondary"),0xffffffff);}
    }
    /** DuoBook's white speech card. */
    private GradientDrawable panel(){return Duo.round(this,Duo.WHITE,18,Duo.LINE,1);}
    /** Ends the reply caption soon after speech finishes; sooner still when a video or music is playing. */
    private void finishCaption(long graceMs){
        long grace=graceMs>=0?graceMs:audioManager!=null&&audioManager.isMusicActive()?400:1500;
        captionUntil=SystemClock.uptimeMillis()+grace;handler.postDelayed(()->refreshBubble(),grace+60);refreshBubble();
    }
    /** Buddy is only on screen while it has something to show, or when the user asked it to stay and nothing is playing. */
    private boolean wantBubble(){
        String phase=state.optString("phase","idle");
        boolean busy=listening||speaking||phase.equals("thinking")||phase.equals("acting")||SystemClock.uptimeMillis()<captionUntil||reacting();
        return busy||(state.optBoolean("overlay",false)&&!(audioManager!=null&&audioManager.isMusicActive()));
    }
    private void refreshBubble(){
        boolean show=!visible&&wantBubble()&&Settings.canDrawOverlays(this);
        if(show&&bubbleRoot==null){
            bubbleRoot=new LinearLayout(this);bubbleRoot.setOrientation(LinearLayout.VERTICAL);bubbleRoot.setGravity(Gravity.RIGHT);
            caption=new TextView(this);caption.setTextColor(Duo.INK);caption.setElevation(dp(4));caption.setTextSize(15);caption.setTypeface(Fonts.get(this,600));caption.setMaxLines(6);caption.setEllipsize(TextUtils.TruncateAt.END);caption.setMaxWidth(dp(420));caption.setPadding(dp(14),dp(10),dp(14),dp(10));caption.setBackground(panel());caption.setVisibility(View.GONE);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.rightMargin=dp(8);cp.bottomMargin=dp(2);bubbleRoot.addView(caption,cp);
            bubble=new FaceView(this,true);bubbleRoot.addView(bubble,new LinearLayout.LayoutParams(dp(150),dp(150)));
            WindowManager.LayoutParams p=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);p.gravity=Gravity.BOTTOM|Gravity.RIGHT;p.x=dp(12);p.y=dp(12);
            bubble.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            try{wm.addView(bubbleRoot,p);}catch(Exception e){bubbleRoot=null;bubble=null;caption=null;}
        }else if(!show&&bubbleRoot!=null){try{wm.removeView(bubbleRoot);}catch(Exception ignored){}bubbleRoot=null;bubble=null;caption=null;}
        if(bubble!=null){bubble.phase=effectivePhase();bubble.talking=speaking;dress(bubble);}
        if(caption!=null){String text=captionText();if(text.isEmpty())caption.setVisibility(View.GONE);else{if(!text.contentEquals(caption.getText()))caption.setText(text);caption.setVisibility(View.VISIBLE);}}
    }
    private int dp(float n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
    public void onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);stopAudio();if(recognizer!=null){recognizer.destroy();recognizer=null;}listening=false;if(bubbleRoot!=null)try{wm.removeView(bubbleRoot);}catch(Exception ignored){}speaking=false;super.onDestroy();}
}
