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
 * Buddy's always-on presence: watches the local runtime, synthesizes speech on the TV, owns the
 * microphone and draws the little companion with live captions over any app.
 */
public final class CompanionService extends Service {
    public static final String LISTEN="family.tv.companion.LISTEN", FINISH_LISTEN="family.tv.companion.FINISH_LISTEN";
    public static volatile JSONObject state=new JSONObject();
    public static volatile boolean visible=false,speaking=false,listening=false,preparingVoice=false;
    public static volatile String heard="",localError="";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WindowManager wm;private LinearLayout bubbleRoot;private TextView caption;private FaceView bubble;
    private MediaPlayer player;private AudioManager audioManager;private SpeechRecognizer recognizer;private LocalPiper piper;
    private final ListeningSession listenSession=new ListeningSession();
    private boolean polling=false,destroyed=false,autoListen=false,followUpListen=false;
    private Api.StateWatch stateWatch;
    private long stateRevision=-1;private String stateSession="";
    private int followUpRequest=0;
    private String lastReply=null,audioError="",captionReply="";
    private long captionUntil=0;
    private final Runnable followUpTimer=()->checkFollowUp();
    static final ReplyDisplay replyDisplay=new ReplyDisplay();
    // A reaction to what's on: Buddy pops in for a few seconds, even over a video.
    private int lastReaction=-1;private long reactionUntil=0;private JSONObject reaction=null;
    public IBinder onBind(Intent i){return null;}
    public void onCreate(){
        super.onCreate();replyDisplay.clear();wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        String channel="buddy";NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel(channel,Duo.name(this),NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(12,new Notification.Builder(this,channel).setContentTitle(Duo.name(this)+" is ready").setContentText("Press the remote’s microphone button to talk. The microphone listens only when requested.").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(open).build());
        audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);
        piper=new LocalPiper(this);piper.warm();
        stateWatch=new Api.StateWatch(this);
        handler.post(poll);
    }
    public int onStartCommand(Intent intent,int flags,int id){
        String action=intent==null?null:intent.getAction();
        if("pause_assistant".equals(action)){autoListen=false;stopAudio();clearListening();presence("idle");}
        if("stop_speaking".equals(action)){autoListen=false;stopAudio();finishCaption(400);}
        if("voice_test".equals(action)){
            clearListening();autoListen=false;captionReply="Hi! I'm your DuoBuddy. Shall we find something good to watch?";
            captionUntil=SystemClock.uptimeMillis()+120000;speakLocal(captionReply,true);
        }
        if("voice_changed".equals(action)){autoListen=false;stopAudio();piper.warm();}
        if("local_timer".equals(action))LocalController.get(this).fireTimer(intent.getIntExtra("id",-1));
        if(LISTEN.equals(action))startListening(false);
        if(FINISH_LISTEN.equals(action))finishListening();
        refreshBubble();return START_STICKY;
    }

    // ---- speech output ----
    private void stopAudio(){followUpRequest++;handler.removeCallbacks(followUpTimer);if(piper!=null)piper.stop();if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}speaking=false;preparingVoice=false;if(audioManager!=null)audioManager.abandonAudioFocus(null);broadcast();}
    private void checkFollowUp(){
        if(destroyed)return;
        // Older controllers checked before publishing the reply.
        if(!state.optBoolean("stateWait",false)){startListening(true);return;}
        final int request=++followUpRequest;
        Api.request(this,"/api/follow-up?replyId="+android.net.Uri.encode(state.optString("replyId","")),null,(data,error)->{
            if(!destroyed&&request==followUpRequest&&!listening&&!speaking&&!preparingVoice&&"idle".equals(state.optString("phase"))&&data!=null&&data.optBoolean("allowed",false))startListening(true);
        });
    }
    private void speechFinished(boolean finalReply){
        speaking=false;preparingVoice=false;broadcast();
        if(finalReply){finishCaption(autoListen?4000:-1);if(autoListen){autoListen=false;handler.postDelayed(followUpTimer,350);}}
    }
    private void speakLocal(String text,boolean finalReply){
        stopAudio();audioError="";if(text.trim().isEmpty())return;
        preparingVoice=true;changed();
        piper.speak(text,new LocalPiper.Listener(){
            public void started(){if(destroyed)return;preparingVoice=false;speaking=true;changed();}
            public void finished(){if(!destroyed)speechFinished(finalReply);}
            public void interrupted(){if(destroyed)return;autoListen=false;speechFinished(finalReply);}
            public void failed(String error){if(destroyed)return;audioError=error;autoListen=false;speaking=false;preparingVoice=false;try{state.put("voiceError",error);}catch(Exception ignored){}finishCaption(-1);changed();}
        });
    }
    // ---- speech input ----
    /** Duplicate native-assistant and controller triggers must never stop an utterance. */
    private void startListening(boolean followUp){
        if(destroyed||listenSession.active())return;
        boolean allowed=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED&&SpeechRecognizer.isRecognitionAvailable(this);
        if(!allowed||(!visible&&!Settings.canDrawOverlays(this))){if(!followUp)startActivity(new Intent(this,MainActivity.class).setAction(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;}
        listenSession.begin(SystemClock.uptimeMillis(),followUp);
        replyDisplay.clear();autoListen=false;followUpListen=followUp;stopAudio();localError="";heard="";
        handler.postDelayed(listenTimeout,ListeningSession.MAX_DURATION_MS);
        beginRecognition();
    }
    private final Runnable retryListening=()->{if(!destroyed&&listenSession.active())beginRecognition();};
    private final Runnable listenTimeout=()->finishListening();
    private final Runnable finishTimeout=()->{if(!listenSession.text().isEmpty())submitTranscript();else endListening("Listening timed out. Please try again.");};
    private final Runnable commitListening=new Runnable(){public void run(){
        if(destroyed||!listenSession.active())return;
        if(listenSession.submit(SystemClock.uptimeMillis()))submitTranscript();
        else handler.postDelayed(this,Math.max(100,listenSession.pauseRemaining(SystemClock.uptimeMillis())));
    }};
    private void releaseRecognizer(){
        SpeechRecognizer old=recognizer;recognizer=null;
        if(old!=null){try{old.cancel();}catch(Exception ignored){}old.destroy();}
    }
    private void finishListening(){
        if(!listenSession.active())return;
        listenSession.stop();handler.removeCallbacks(retryListening);handler.removeCallbacks(listenTimeout);handler.removeCallbacks(commitListening);
        if(listenSession.hasResult()&&!listenSession.awaitingResult()){submitTranscript();return;}
        if(recognizer==null){endListening("");return;}
        android.util.Log.i("BuddyVoice","Finishing speech input");
        try{recognizer.stopListening();handler.removeCallbacks(finishTimeout);handler.postDelayed(finishTimeout,3000);}
        catch(Exception e){endListening("Microphone unavailable. Please try again.");}
    }
    private void clearListening(){
        listenSession.close();handler.removeCallbacks(retryListening);handler.removeCallbacks(listenTimeout);handler.removeCallbacks(finishTimeout);handler.removeCallbacks(commitListening);
        releaseRecognizer();listening=false;
    }
    private void endListening(String error){
        clearListening();presence("idle");heard="";localError=error;
        if(!error.isEmpty())captionUntil=SystemClock.uptimeMillis()+6000;
        changed();
    }
    private void recognitionFailed(int attempt,int error){
        if(destroyed||!listenSession.current(attempt))return;
        boolean empty=error==SpeechRecognizer.ERROR_NO_MATCH||error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
        boolean transientFailure=error==SpeechRecognizer.ERROR_RECOGNIZER_BUSY||error==SpeechRecognizer.ERROR_AUDIO||error==SpeechRecognizer.ERROR_CLIENT;
        android.util.Log.i("BuddyVoice","Speech error "+error);
        if(empty){
            if(listenSession.stopping()&&!listenSession.text().isEmpty()){submitTranscript();return;}
            listenSession.emptyResult();
            if(listenSession.hasResult()&&(listenSession.stopping()||listenSession.pauseRemaining(SystemClock.uptimeMillis())==0)){submitTranscript();return;}
        }
        if(listenSession.retry(SystemClock.uptimeMillis(),empty,transientFailure)){
            // Invalidate this listener before cancellation can deliver stale callbacks.
            listenSession.attempt(SystemClock.uptimeMillis());releaseRecognizer();heard=listenSession.text();
            android.util.Log.i("BuddyVoice","Speech input ended early; retrying");
            handler.postDelayed(retryListening,400);changed();return;
        }
        endListening(followUpListen&&empty?"":empty?"I didn’t catch that. Try again.":"Microphone unavailable ("+error+"). You can type a request in DuoBuddy.");
    }
    private void beginRecognition(){
        final int attempt=listenSession.attempt(SystemClock.uptimeMillis());
        try{
            // Reuse a healthy recognizer after a final result; errors still recreate it.
            if(recognizer==null)recognizer=SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener(){
                public void onReadyForSpeech(Bundle b){if(!listenSession.current(attempt)||destroyed)return;listenSession.ready(SystemClock.uptimeMillis());android.util.Log.i("BuddyVoice","Speech input ready");listening=true;presence("listening");changed();}
                public void onBeginningOfSpeech(){if(listenSession.current(attempt)){listenSession.speech(SystemClock.uptimeMillis());android.util.Log.i("BuddyVoice","Speech started");}}
                public void onRmsChanged(float r){}public void onBufferReceived(byte[] b){}public void onEvent(int e,Bundle b){}
                public void onEndOfSpeech(){if(listenSession.current(attempt))android.util.Log.i("BuddyVoice","Speech endpoint reached");}
                public void onError(int e){recognitionFailed(attempt,e);}
                public void onResults(Bundle b){
                    if(!listenSession.current(attempt)||destroyed)return;
                    ArrayList<String> results=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String text=results==null||results.isEmpty()?"":results.get(0).trim();
                    if(text.isEmpty()){recognitionFailed(attempt,SpeechRecognizer.ERROR_NO_MATCH);return;}
                    android.util.Log.i("BuddyVoice","Speech segment received");
                    listenSession.result(SystemClock.uptimeMillis(),text);heard=listenSession.text();
                    if(listenSession.stopping()){submitTranscript();return;}
                    // Do not submit a whole request just because the engine ended a short segment.
                    listenSession.attempt(SystemClock.uptimeMillis());
                    handler.removeCallbacks(commitListening);handler.postDelayed(commitListening,ListeningSession.CONTINUATION_MS);
                    handler.removeCallbacks(retryListening);handler.postDelayed(retryListening,150);changed();
                }
                public void onPartialResults(Bundle b){if(!listenSession.current(attempt)||destroyed)return;ArrayList<String> r=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(r!=null&&!r.isEmpty()){listenSession.partial(SystemClock.uptimeMillis(),r.get(0));heard=listenSession.text();changed();}}
            });
            Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US");i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
            // These are hints: the bounded retry window also handles engines that ignore them.
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,3000L);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,2500L);
            recognizer.startListening(i);
        }catch(Exception e){recognitionFailed(attempt,SpeechRecognizer.ERROR_CLIENT);}
    }
    private void submitTranscript(){
        if(!listenSession.active())return;
        String text=listenSession.text();
        if(text.isEmpty()){endListening(followUpListen?"":"I didn’t catch that. Try again.");return;}
        android.util.Log.i("BuddyVoice","Speech transcription submitted");clearListening();sendChat(text);
    }
    private void sendChat(String text){
        stopAudio();heard="";localError="";
        Api.request(this,"/api/chat",Api.json("text",text,"followUp",followUpListen?"1":"0"),(r,e)->{if(e!=null){localError=e;captionUntil=SystemClock.uptimeMillis()+6000;}changed();});
        changed();
    }
    private void presence(String phase){Api.request(this,"/api/presence",Api.json("phase",phase),null);}
    private void changed(){broadcast();refreshBubble();}
    private void broadcast(){Intent i=new Intent("family.tv.companion.STATE");i.setPackage(getPackageName());sendBroadcast(i);handler.post(()->{if(bubble!=null){bubble.phase=effectivePhase();bubble.talking=speaking;}});}
    private String effectivePhase(){return listening?"listening":preparingVoice?"thinking":state.optString("phase","idle");}

    // ---- immediate local state delivery ----
    private final Runnable poll=new Runnable(){public void run(){
        if(destroyed)return;
        if(!Api.paired(CompanionService.this)){handler.postDelayed(this,2000);return;}
        if(polling){handler.postDelayed(this,1200);return;}polling=true;
        stateWatch.request(stateRevision,stateSession,(data,error)->{
            polling=false;if(destroyed)return;
            if(data!=null){
                String session=data.optString("session","");
                if(!session.equals(stateSession)){lastReply=data.optBoolean("standalone")?Api.prefs(CompanionService.this).getString("native_last_reply",""):null;stateSession=session;}
                stateRevision=data.optLong("revision",-1);
                state=data;if(listening||"thinking".equals(data.optString("phase"))||"acting".equals(data.optString("phase")))replyDisplay.clear();String id=data.optString("replyId","");
                if(lastReply==null)lastReply=id;
                else if(!id.equals(lastReply)){
                    lastReply=id;if(data.optBoolean("standalone"))Api.prefs(CompanionService.this).edit().putString("native_last_reply",id).apply();boolean finalReply="idle".equals(data.optString("phase",""))&&!data.optString("reply","").isEmpty();
                    if(finalReply)android.util.Log.i("BuddyVoice","Reply received: id="+id+"; revision="+stateRevision);
                    autoListen=finalReply&&data.optBoolean("followUp",false);
                    // The caption stays while Buddy speaks and leaves shortly after (see finishCaption);
                    // with no audio to wait for, it shows for a few seconds of reading time.
                    if(finalReply)replyDisplay.received(id,SystemClock.uptimeMillis());else replyDisplay.clear();
                    if(finalReply){captionReply=data.optString("reply","");captionUntil=SystemClock.uptimeMillis()+120000;}
                    String speech=data.optString("speechText",finalReply?data.optString("reply",""):"");
                    if(!speech.isEmpty())speakLocal(speech,finalReply);
                    else stopAudio();
                }
                JSONObject r=data.optJSONObject("reaction");int rid=r==null?0:r.optInt("id",0);
                // The first poll only records the latest reaction, so a restart doesn't replay it.
                if(lastReaction<0)lastReaction=rid;
                else if(rid!=lastReaction){lastReaction=rid;reaction=r;long ms=r==null?0:Math.max(1500,Math.min(10000,r.optLong("ms",4500)));reactionUntil=SystemClock.uptimeMillis()+ms;handler.postDelayed(()->refreshBubble(),ms+80);}
                if(!audioError.isEmpty())try{state.put("voiceError",audioError);}catch(Exception ignored){}
                broadcast();
            }else{try{state.put("status","Local runtime unavailable");state.put("phase","idle");state.put("error",error);}catch(Exception ignored){}broadcast();}
            refreshBubble();handler.postDelayed(poll,data!=null&&data.optBoolean("stateWait",false)?0:1300);
        });
    }};

    // ---- overlay ----
    private String captionText(){
        long now=SystemClock.uptimeMillis();String phase=state.optString("phase","idle");
        if(listening)return heard.isEmpty()?"Listening…":heard;
        if(!localError.isEmpty()&&now<captionUntil)return localError;
        if(phase.equals("thinking")||phase.equals("acting")){String user=state.optString("user","");return phase.equals("acting")?"On it…":user.isEmpty()?"Thinking…":"“"+user+"”";}
        if((preparingVoice||speaking||now<captionUntil)&&!captionReply.isEmpty())return captionReply;
        if(reacting())return reaction.optString("caption","");
        return "";
    }
    private boolean reacting(){return reaction!=null&&SystemClock.uptimeMillis()<reactionUntil;}
    private boolean busyPhase(){String phase=state.optString("phase","idle");return listening||preparingVoice||speaking||phase.equals("thinking")||phase.equals("acting");}
    private static int hex(String c,int fallback){try{return android.graphics.Color.parseColor(c);}catch(Exception e){return fallback;}}
    /** Gives the bubble the reaction's pose, outfit and scarf colours while it lasts. */
    private void dress(FaceView f){
        boolean on=reacting()&&!busyPhase();
        f.setMood(on?reaction.optString("mood",""):"");f.dressUp=on?reaction.optJSONObject("outfit"):null;
        JSONObject sc=on?reaction.optJSONObject("scarf"):null;if(sc!=null){f.scarfMain=hex(sc.optString("primary"),0xffe11d48);f.scarfStripe=hex(sc.optString("secondary"),0xffffffff);}
    }
    /** Buddy's speech card in the selected appearance. */
    private GradientDrawable panel(){Duo.Palette colors=Duo.palette(this);return Duo.round(this,colors.SURFACE,18,colors.LINE,1);}
    /** Ends the reply caption soon after speech finishes; sooner still when a video or music is playing. */
    private void finishCaption(long graceMs){
        replyDisplay.finished(SystemClock.uptimeMillis());
        long grace=graceMs>=0?graceMs:audioManager!=null&&audioManager.isMusicActive()?400:1500;
        captionUntil=SystemClock.uptimeMillis()+grace;handler.postDelayed(()->refreshBubble(),grace+60);refreshBubble();
    }
    /** Buddy is only on screen while it has something to show, or when the user asked it to stay and nothing is playing. */
    private boolean wantBubble(){
        String phase=state.optString("phase","idle");
        boolean busy=listening||preparingVoice||speaking||phase.equals("thinking")||phase.equals("acting")||SystemClock.uptimeMillis()<captionUntil||reacting();
        return busy||(state.optBoolean("overlay",false)&&!(audioManager!=null&&audioManager.isMusicActive()));
    }
    private void refreshBubble(){
        boolean show=!visible&&wantBubble()&&Settings.canDrawOverlays(this);
        if(show&&bubbleRoot==null){
            bubbleRoot=new LinearLayout(this);bubbleRoot.setOrientation(LinearLayout.VERTICAL);bubbleRoot.setGravity(Gravity.RIGHT);
            caption=new TextView(this);caption.setTextColor(Duo.palette(this).INK);caption.setElevation(dp(4));caption.setTextSize(15);caption.setTypeface(Fonts.get(this,600));caption.setMaxLines(6);caption.setEllipsize(TextUtils.TruncateAt.END);caption.setMaxWidth(dp(420));caption.setPadding(dp(14),dp(10),dp(14),dp(10));caption.setBackground(panel());caption.setVisibility(View.GONE);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.rightMargin=dp(8);cp.bottomMargin=dp(2);bubbleRoot.addView(caption,cp);
            bubble=new FaceView(this,true);bubbleRoot.addView(bubble,new LinearLayout.LayoutParams(dp(150),dp(150)));
            WindowManager.LayoutParams p=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);p.gravity=Gravity.BOTTOM|Gravity.RIGHT;p.x=dp(12);p.y=dp(12);
            bubble.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            try{wm.addView(bubbleRoot,p);}catch(Exception e){bubbleRoot=null;bubble=null;caption=null;}
        }else if(!show&&bubbleRoot!=null){try{wm.removeView(bubbleRoot);}catch(Exception ignored){}bubbleRoot=null;bubble=null;caption=null;}
        if(bubble!=null){bubble.phase=effectivePhase();bubble.talking=speaking;dress(bubble);}
        if(caption!=null){caption.setTextColor(Duo.palette(this).INK);caption.setBackground(panel());String text=captionText();if(text.isEmpty())caption.setVisibility(View.GONE);else{if(!text.contentEquals(caption.getText()))caption.setText(text);caption.setVisibility(View.VISIBLE);}}
    }
    private int dp(float n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
    public void onDestroy(){destroyed=true;if(stateWatch!=null)stateWatch.close();handler.removeCallbacksAndMessages(null);stopAudio();if(piper!=null)piper.close();clearListening();if(bubbleRoot!=null)try{wm.removeView(bubbleRoot);}catch(Exception ignored){}speaking=false;super.onDestroy();}
}
