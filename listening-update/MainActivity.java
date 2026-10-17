package family.tv.companion;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private FaceView face;private TextView status,reply,heard,note,greeting,talkLabel;private View talk;private EditText input;private boolean pendingAssist=false,resumed=false;private String localHeard="";
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){render();}};
    public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);configure(getIntent());build();startForegroundService(new Intent(this,CompanionService.class));}
    protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);configure(i);if(i.getBooleanExtra("voice_test",false))startForegroundService(new Intent(this,CompanionService.class).setAction("voice_test"));render();beginPendingAssist();}
    private void configure(Intent i){if(Intent.ACTION_ASSIST.equals(i.getAction()))pendingAssist=true;String server=i.getStringExtra("server"),token=i.getStringExtra("token");if(server!=null&&token!=null&&server.matches("http://[0-9.]+:[0-9]+")&&token.matches("[a-f0-9]{64}"))Api.prefs(this).edit().putString("server",server).putString("token",token).apply();}
    private void beginPendingAssist(){if(!pendingAssist||!resumed)return;pendingAssist=false;android.util.Log.i("BuddyVoice","Remote assistant invoked");new Handler(Looper.getMainLooper()).postDelayed(()->{if(resumed&&!controllerHandlesRemote()&&!CompanionService.listening)listen();},350);}
    protected void onResume(){super.onResume();resumed=true;CompanionService.visible=true;registerReceiver(receiver,new IntentFilter("family.tv.companion.STATE"));startForegroundService(new Intent(this,CompanionService.class));render();beginPendingAssist();}
    protected void onPause(){super.onPause();resumed=false;CompanionService.visible=false;try{unregisterReceiver(receiver);}catch(Exception ignored){}startForegroundService(new Intent(this,CompanionService.class));}
    private int dp(float v){return Duo.dp(this,v);}
    /** Buddy on a soft stage at left; at right a greeting, the question or Buddy's reply, suggestions, and one row of actions. */
    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.HORIZONTAL);root.setBackgroundColor(Duo.CANVAS);root.setPadding(dp(48),dp(38),dp(56),dp(34));setContentView(root);
        LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);root.addView(left,new LinearLayout.LayoutParams(0,-1,.88f));
        left.addView(Duo.text(this,"DuoBook",19,Duo.INK,700));
        FrameLayout stage=new FrameLayout(this);stage.addView(new Duo.Stage(this),new FrameLayout.LayoutParams(-1,-1));
        face=new FaceView(this,true);stage.addView(face,new FrameLayout.LayoutParams(dp(300),dp(300),Gravity.CENTER));
        left.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        status=Duo.text(this,"",14,Duo.SECOND,600);status.setGravity(Gravity.CENTER);left.addView(status,new LinearLayout.LayoutParams(-1,-2));
        note=status;

        LinearLayout right=new LinearLayout(this);right.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,1.12f);rp.leftMargin=dp(40);root.addView(right,rp);
        greeting=Duo.text(this,"",18,Duo.SECOND,600);LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,-2);gp.topMargin=dp(30);right.addView(greeting,gp);
        ScrollView scroll=new ScrollView(this);scroll.setFocusable(false);scroll.setVerticalScrollBarEnabled(false);
        reply=Duo.text(this,"What would you like to do?",30,Duo.INK,700);reply.setLineSpacing(dp(6),1);scroll.addView(reply);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.topMargin=dp(12);right.addView(scroll,sp);
        heard=Duo.text(this,"",14,Duo.MUTED,600);heard.setMaxLines(2);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.bottomMargin=dp(12);right.addView(heard,hp);
        String[][] starters={{"play","Play lo-fi on YouTube","Play lo-fi on YouTube"},{"music","What’s playing?","What’s playing right now?"},{"timer","Set a 10-minute timer","Set a ten minute timer"},{"moon","Show the screensaver",""}};
        for(String[] st:starters){LinearLayout r=Duo.row(this,st[0],st[1],()->{if(st[2].isEmpty())startActivity(new Intent(this,AmbientActivity.class));else send(st[2]);});right.addView(r,new LinearLayout.LayoutParams(-1,dp(48)));}
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout t=Duo.primary(this,"mic","Talk",()->{if(CompanionService.listening)startForegroundService(new Intent(this,CompanionService.class).setAction(CompanionService.FINISH_LISTEN));else listen();});talk=t;talkLabel=(TextView)t.getChildAt(1);actions.addView(t,new LinearLayout.LayoutParams(-2,dp(48)));
        for(View v:new View[]{Duo.quiet(this,"apps","Apps",()->chooseApp()),Duo.quiet(this,"style","Style",()->chooseStyle()),Duo.quiet(this,"keyboard","Type",()->typeRequest())}){LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-2,dp(48));ap.leftMargin=dp(8);actions.addView(v,ap);}
        // Room around the row so focus shadows and the Talk button's lift aren't clipped.
        actions.setPadding(dp(4),dp(8),dp(4),dp(10));actions.setClipToPadding(false);right.setClipChildren(false);right.setClipToPadding(false);root.setClipChildren(false);
        LinearLayout.LayoutParams acp=new LinearLayout.LayoutParams(-1,-2);acp.topMargin=dp(10);acp.leftMargin=-dp(4);right.addView(actions,acp);
        input=new EditText(this);talk.requestFocus();
    }
    /** Typing opens a text box, so the action row stays as simple as the remote. */
    private void typeRequest(){
        input=new EditText(this);input.setSingleLine(true);input.setTextSize(17);input.setTypeface(Fonts.get(this,600));input.setTextColor(Duo.INK);input.setHintTextColor(Duo.MUTED);input.setHint("What should Buddy do?");
        input.setPadding(dp(18),dp(12),dp(18),dp(12));input.setBackground(Duo.round(this,Duo.CANVAS,14,Duo.LINE,1));input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        FrameLayout box=new FrameLayout(this);box.setPadding(dp(22),dp(8),dp(22),0);box.addView(input);
        final AlertDialog[] shown=new AlertDialog[1];
        input.setOnEditorActionListener((v,a,e)->{send(input.getText().toString());if(shown[0]!=null)shown[0].dismiss();return true;});
        shown[0]=Fonts.show(Fonts.dialog(this).setTitle("Type a request").setView(box).setPositiveButton("Send",(d,w)->send(input.getText().toString())).setNegativeButton("Cancel",null));
        input.requestFocus();
    }
    private String greetingText(){
        int h=java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);String person=CompanionService.state.optString("person","");
        return (h<5?"Good night":h<12?"Good morning":h<18?"Good afternoon":h<22?"Good evening":"Good night")+(person.isEmpty()?"":", "+person);
    }
    private void render(){if(status==null)return;JSONObject s=CompanionService.state;boolean listening=CompanionService.listening;String phase=listening?"listening":s.optString("phase","idle");face.phase=phase;face.talking=CompanionService.speaking;
        String message=listening?"Listening":s.optString("status",Api.paired(this)?"Connecting to your Mac":"Pair this TV from your Mac");
        int dot=listening?Duo.SKY:phase.equals("thinking")||phase.equals("acting")?Duo.ORANGE:Duo.GREEN;
        android.text.SpannableString pill=new android.text.SpannableString("●  "+message);pill.setSpan(new android.text.style.ForegroundColorSpan(dot),0,1,0);status.setText(pill);
        String answer=s.optString("reply",""),user=s.optString("user","");
        if(!answer.isEmpty()){reply.setText(answer);reply.setTextSize(answer.length()>140?22:answer.length()>70?26:30);greeting.setText(user.isEmpty()?greetingText():"You asked: “"+user+"”");}
        else{greeting.setText(greetingText());if(!s.optBoolean("configured",true)&&Api.paired(this))reply.setText("Add your API key on the Mac setup page, then press Talk.");}
        String error=CompanionService.localError.isEmpty()?s.optString("voiceError",s.optString("error","")):CompanionService.localError;
        heard.setText(listening&&!CompanionService.heard.isEmpty()?"“"+CompanionService.heard+"”":error.isEmpty()?(answer.isEmpty()?localHeard:""):error);
        heard.setTextColor(error.isEmpty()||listening?Duo.MUTED:0xffdc2626);
        talkLabel.setText(listening?"Listening…":"Talk");
    }
    private void manual(String tool,JSONObject args){stopSpeaking();try{Api.request(this,"/api/action",new JSONObject().put("tool",tool).put("args",args),(r,e)->{if(e!=null)heard.setText(e);});}catch(Exception e){heard.setText(e.getMessage());}}
    /** Lists every app installed on the TV, so Buddy's launcher is never out of date. */
    private void chooseApp(){
        Api.request(this,"/api/apps",null,(data,error)->{
            if(data==null){heard.setText(error==null?"Could not load apps.":error);return;}
            JSONArray list=data.optJSONArray("apps");if(list==null||list.length()==0){heard.setText("No apps found.");return;}
            String[] keys=new String[list.length()],labels=new String[list.length()];
            for(int i=0;i<list.length();i++){JSONObject a=list.optJSONObject(i);keys[i]=a.optString("key");labels[i]=a.optString("label");}
            Fonts.show(Fonts.dialog(this).setTitle("Open an app").setItems(labels,(d,which)->openApp(keys[which])).setNegativeButton("Cancel",null));
        });
    }
    private void openApp(String key){manual("open_app",Api.json("app",key));}
    /** Colour plus DuoBook's four wardrobe slots, each picked from a remote-friendly list. */
    private void chooseStyle(){
        String[] slots={"variant","hat","eyewear","neck","face"},titles={"Colour","Hat","Eyewear","Neck","Face"};String[] labels=new String[slots.length+1];
        for(int i=0;i<slots.length;i++)labels[i]=titles[i]+" · "+styleName(slots[i],Api.prefs(this).getString(slots[i],i==0?"tangerine":""));
        JSONObject look=CompanionService.state.optJSONObject("look");
        labels[slots.length]="Screensaver preview"+(look!=null&&look.has("label")?"  ·  dressed up for "+look.optString("label"):"");
        Fonts.show(Fonts.dialog(this).setTitle("Buddy’s style").setItems(labels,(d,which)->{if(which==slots.length)startActivity(new Intent(this,AmbientActivity.class));else chooseStyle(slots[which],titles[which]);}).setNegativeButton("Done",null));
    }
    private String styleName(String slot,String id){
        if(slot.equals("variant")){String[] v=VARIANTS;for(int i=0;i<v.length;i++)if(v[i].equals(id))return VARIANT_NAMES[i];return VARIANT_NAMES[0];}
        JSONArray all=FaceView.wardrobe(this);for(int i=0;i<all.length();i++){JSONObject o=all.optJSONObject(i);if(o.optString("id").equals(id))return o.optString("name");}return "None";
    }
    private static final String[] VARIANTS={"tangerine","fern","sky","plum","berry","sunshine","midnight","lagoon","rose","ivory","obsidian","lavender"};
    private static final String[] VARIANT_NAMES={"Tangerine","Fern","Sky","Plum","Berry","Sunshine","Midnight","Lagoon","Rose","Ivory","Obsidian","Lavender"};
    private void chooseStyle(String slot,String title){
        java.util.ArrayList<String> ids=new java.util.ArrayList<>(),names=new java.util.ArrayList<>();
        if(slot.equals("variant"))for(int i=0;i<VARIANTS.length;i++){ids.add(VARIANTS[i]);names.add(VARIANT_NAMES[i]);}
        else{ids.add("");names.add("None");JSONArray all=FaceView.wardrobe(this);for(int i=0;i<all.length();i++){JSONObject o=all.optJSONObject(i);if(o.optString("slot").equals(slot)){ids.add(o.optString("id"));names.add(o.optString("name"));}}}
        String current=Api.prefs(this).getString(slot,slot.equals("variant")?"tangerine":"");int selected=Math.max(0,ids.indexOf(current));
        // Picking applies at once and returns to the slot list, so outfits can be tried in a row.
        Fonts.show(Fonts.dialog(this).setTitle(title).setSingleChoiceItems(names.toArray(new String[0]),selected,(dialog,which)->{Api.prefs(this).edit().putString(slot,ids.get(which)).apply();face.invalidate();dialog.dismiss();chooseStyle();}).setNegativeButton("Back",(d,w)->chooseStyle()));
    }
    private void stopSpeaking(){startForegroundService(new Intent(this,CompanionService.class).setAction("stop_speaking"));}
    private void send(String text){text=text.trim();if(text.isEmpty())return;final String sent=text;CompanionService.localError="";localHeard="You: "+sent;stopSpeaking();input.setText("");heard.setText(localHeard);status.setText("●  Thinking");face.phase="thinking";Api.request(this,"/api/chat",Api.json("text",sent),(r,e)->{if(e!=null){CompanionService.localError=e;heard.setText(e);face.phase="idle";status.setText("●  Ready");}});}
    private boolean controllerHandlesRemote(){return CompanionService.state.optBoolean("remoteButtonEnabled")&&CompanionService.state.optBoolean("remoteButtonReady");}
    private void listen(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10);return;}
        startForegroundService(new Intent(this,CompanionService.class).setAction(CompanionService.LISTEN));
    }
    public void onRequestPermissionsResult(int r,String[] p,int[] grants){super.onRequestPermissionsResult(r,p,grants);if(r==10&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)listen();else note.setText("Microphone permission is needed for Talk. Typing remains available.");}
    public boolean onKeyDown(int key,KeyEvent e){if(key==KeyEvent.KEYCODE_SEARCH||key==KeyEvent.KEYCODE_VOICE_ASSIST||key==KeyEvent.KEYCODE_ASSIST){if(e.getRepeatCount()==0&&!controllerHandlesRemote())listen();return true;}if(key==KeyEvent.KEYCODE_MEDIA_STOP){stopSpeaking();Api.request(this,"/api/stop",new JSONObject(),null);return true;}return super.onKeyDown(key,e);}
}
