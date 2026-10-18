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
    private Duo.Palette colors;
    private FaceView face;private TextView status,reply,heard,note,greeting,talkLabel;private View talk;private EditText input;private boolean pendingAssist=false,resumed=false;
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){render();}};
    public void onCreate(Bundle b){colors=Duo.palette(this);setTheme(Duo.theme(this));super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);configure(getIntent());build();startForegroundService(new Intent(this,CompanionService.class));}
    protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);configure(i);if(i.getBooleanExtra("voice_test",false))startForegroundService(new Intent(this,CompanionService.class).setAction("voice_test"));render();beginPendingAssist();}
    private void configure(Intent i){if(Intent.ACTION_ASSIST.equals(i.getAction()))pendingAssist=true;}
    private void beginPendingAssist(){if(!pendingAssist||!resumed)return;pendingAssist=false;android.util.Log.i("BuddyVoice","Remote assistant invoked");new Handler(Looper.getMainLooper()).postDelayed(()->{if(resumed&&!controllerHandlesRemote()&&!CompanionService.listening)listen();},350);}
    protected void onResume(){super.onResume();if(colors!=Duo.palette(this)){recreate();return;}resumed=true;CompanionService.visible=true;registerReceiver(receiver,new IntentFilter("family.tv.companion.STATE"));startForegroundService(new Intent(this,CompanionService.class));render();beginPendingAssist();}
    protected void onPause(){super.onPause();resumed=false;CompanionService.visible=false;try{unregisterReceiver(receiver);}catch(Exception ignored){}startForegroundService(new Intent(this,CompanionService.class));}
    private int dp(float v){return Duo.dp(this,v);}
    /** Buddy on a soft stage at left; at right a greeting, the question or Buddy's reply, suggestions, and one row of actions. */
    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(colors.CANVAS);root.setPadding(dp(48),dp(38),dp(56),dp(34));setContentView(root);
        LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.addView(Duo.text(this,Duo.name(this),22,colors.INK,700),new LinearLayout.LayoutParams(0,-2,1));
        brand.addView(Duo.quiet(this,"model","Model",()->chooseModel()),new LinearLayout.LayoutParams(-2,dp(44)));
        brand.addView(Duo.quiet(this,"music","Voice",()->chooseVoice()),new LinearLayout.LayoutParams(-2,dp(44)));
        brand.addView(Duo.quiet(this,Duo.night(this)?"moon":"sun","Appearance",()->chooseAppearance()),new LinearLayout.LayoutParams(-2,dp(44)));
        brand.setClipChildren(false);root.addView(brand,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout content=new LinearLayout(this);content.setClipChildren(false);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);content.addView(left,new LinearLayout.LayoutParams(0,-1,.88f));
        FrameLayout stage=new FrameLayout(this);stage.addView(new Duo.Stage(this),new FrameLayout.LayoutParams(-1,-1));
        face=new FaceView(this,true);stage.addView(face,new FrameLayout.LayoutParams(dp(300),dp(300),Gravity.CENTER));
        left.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        status=Duo.text(this,"",14,colors.SECOND,600);status.setGravity(Gravity.CENTER);left.addView(status,new LinearLayout.LayoutParams(-1,-2));
        note=status;

        LinearLayout right=new LinearLayout(this);right.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,1.12f);rp.leftMargin=dp(40);content.addView(right,rp);
        greeting=Duo.text(this,"",18,colors.SECOND,600);LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,-2);gp.topMargin=dp(16);right.addView(greeting,gp);
        ScrollView scroll=new ScrollView(this);scroll.setFocusable(false);scroll.setVerticalScrollBarEnabled(false);
        reply=Duo.text(this,"What shall we do today?",30,colors.INK,700);reply.setLineSpacing(dp(6),1);scroll.addView(reply);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.topMargin=dp(12);right.addView(scroll,sp);
        heard=Duo.text(this,"",14,colors.MUTED,600);heard.setMaxLines(2);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.bottomMargin=dp(12);right.addView(heard,hp);
        String[][] starters={{"play","Play lo-fi on YouTube","Play lo-fi on YouTube"},{"music","What’s playing?","What’s playing right now?"},{"timer","Set a 10-minute timer","Set a ten minute timer"},{"moon","Show the screensaver",""}};
        for(String[] st:starters){LinearLayout r=Duo.row(this,st[0],st[1],()->{if(st[2].isEmpty())startActivity(new Intent(this,AmbientActivity.class));else send(st[2]);});right.addView(r,new LinearLayout.LayoutParams(-1,dp(48)));}
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout t=Duo.primary(this,"mic","Talk",()->{if(CompanionService.listening)startForegroundService(new Intent(this,CompanionService.class).setAction(CompanionService.FINISH_LISTEN));else listen();});talk=t;talkLabel=(TextView)t.getChildAt(1);actions.addView(t,new LinearLayout.LayoutParams(-2,dp(48)));
        for(View v:new View[]{Duo.quiet(this,"apps","Apps",()->chooseApp()),Duo.quiet(this,"style","Style",()->chooseStyle()),Duo.quiet(this,"keyboard","Type",()->typeRequest())}){LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-2,dp(48));ap.leftMargin=dp(8);actions.addView(v,ap);}
        // Room around the row so focus shadows and the Talk button's lift aren't clipped.
        actions.setPadding(dp(4),dp(8),dp(4),dp(10));actions.setClipChildren(false);actions.setClipToPadding(false);right.setClipChildren(false);right.setClipToPadding(false);root.setClipChildren(false);root.setClipToPadding(false);
        LinearLayout.LayoutParams acp=new LinearLayout.LayoutParams(-1,-2);acp.topMargin=dp(10);acp.leftMargin=-dp(4);right.addView(actions,acp);
        input=new EditText(this);talk.requestFocus();
    }
    /** Typing opens a text box, so the action row stays as simple as the remote. */
    private void typeRequest(){
        input=new EditText(this);input.setSingleLine(true);input.setTextSize(17);input.setTypeface(Fonts.get(this,600));input.setTextColor(colors.INK);input.setHintTextColor(colors.MUTED);input.setHint("Ask "+Duo.name(this)+" anything");
        input.setPadding(dp(18),dp(12),dp(18),dp(12));input.setBackground(Duo.round(this,colors.CANVAS,14,colors.LINE,1));input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        FrameLayout box=new FrameLayout(this);box.setPadding(dp(22),dp(8),dp(22),0);box.addView(input);
        final AlertDialog[] shown=new AlertDialog[1];
        input.setOnEditorActionListener((v,a,e)->{send(input.getText().toString());if(shown[0]!=null)shown[0].dismiss();return true;});
        shown[0]=Fonts.show(Fonts.dialog(this).setTitle("Type a request").setView(box).setPositiveButton("Send",(d,w)->send(input.getText().toString())).setNegativeButton("Cancel",null));
        input.requestFocus();
    }
    private void chooseAppearance(){
        String[] modes={"system","light","night"},labels={"Follow TV","Light","Night"};
        int selected=java.util.Arrays.asList(modes).indexOf(Duo.appearance(this));
        Fonts.show(Fonts.dialog(this).setTitle("Appearance").setSingleChoiceItems(labels,Math.max(0,selected),(dialog,which)->{
            String mode=modes[which];boolean changed=!mode.equals(Duo.appearance(this));
            Api.prefs(this).edit().putString("appearance",mode).apply();dialog.dismiss();
            if(changed)recreate();
        }).setNegativeButton("Done",null));
    }
    private void chooseVoice(){
        java.util.List<VoiceStore.Voice> voices=VoiceStore.list(this);String[] labels=new String[voices.size()];
        String selected=VoiceStore.selected(this).id;int chosen=0;
        for(int i=0;i<voices.size();i++){VoiceStore.Voice v=voices.get(i);if(v.id.equals(selected))chosen=i;labels[i]=v.label+" · "+(v.id.equals(VoiceStore.SYSTEM)?"built in · fastest":v.bundled?"included · offline":VoiceStore.installed(this,v)?"downloaded":v.size()+" download");}
        Fonts.show(Fonts.dialog(this).setTitle("Voice").setSingleChoiceItems(labels,chosen,(dialog,index)->{dialog.dismiss();voiceDetails(voices.get(index));}).setNegativeButton("Done",null));
    }
    private void chooseModel(){
        Api.request(this,"/api/models",null,(data,error)->{
            if(isFinishing()||isDestroyed())return;
            if(data==null){Fonts.show(Fonts.dialog(this).setTitle("AI model").setMessage(error==null?"Could not load models.":error).setPositiveButton("OK",null));return;}
            JSONArray providers=data.optJSONArray("providers");
            java.util.ArrayList<String> labels=new java.util.ArrayList<>(),ids=new java.util.ArrayList<>(),owners=new java.util.ArrayList<>();
            java.util.ArrayList<Boolean> ready=new java.util.ArrayList<>();int selected=-1;
            if(providers!=null)for(int i=0;i<providers.length();i++){
                JSONObject owner=providers.optJSONObject(i);if(owner==null)continue;JSONArray models=owner.optJSONArray("models");if(models==null)continue;
                String saved=owner.optString("savedModel","");boolean savedListed=false;
                for(int j=0;j<models.length();j++){
                    JSONObject m=models.optJSONObject(j);if(m==null)continue;
                    String id=m.optString("id");if(id.equals(saved))savedListed=true;
                    ids.add(id);owners.add(owner.optString("id"));ready.add(owner.optBoolean("savedKey"));
                    labels.add(m.optString("label",id).replace(" · economical","")+" · "+owner.optString("label")+(owner.optBoolean("savedKey")?"":" (key needed)"));
                }
                if(!savedListed&&!saved.isEmpty()&&owner.optBoolean("savedKey")){
                    ids.add(saved);owners.add(owner.optString("id"));ready.add(true);labels.add(saved+" · "+owner.optString("label"));
                }
            }
            for(int i=0;i<ids.size();i++)if(ids.get(i).equals(data.optString("model"))&&owners.get(i).equals(data.optString("provider")))selected=i;
            if(labels.isEmpty()){Fonts.show(Fonts.dialog(this).setTitle("AI model").setMessage("No models available.").setPositiveButton("OK",null));return;}
            Fonts.show(Fonts.dialog(this).setTitle("AI model").setSingleChoiceItems(labels.toArray(new String[0]),selected,(dialog,which)->{
                dialog.dismiss();
                if(!ready.get(which)){Fonts.show(Fonts.dialog(this).setTitle("API key needed").setMessage("Add this provider’s API key on this TV before using "+ids.get(which)+". Your current model is unchanged.").setPositiveButton("Set up on phone",(d,w)->startActivity(new Intent(this,KeySetupActivity.class).putExtra("provider",owners.get(which)).putExtra("model",ids.get(which)))).setNegativeButton("Cancel",null));return;}
                heard.setText("Connecting to "+ids.get(which)+"…");
                Api.request(this,"/api/model",Api.json("provider",owners.get(which),"model",ids.get(which)),(result,failure)->{
                    if(isFinishing()||isDestroyed())return;
                    if(failure!=null){heard.setText(failure);Fonts.show(Fonts.dialog(this).setTitle("Couldn’t switch model").setMessage(failure).setPositiveButton("OK",null));}
                    else{Api.prefs(this).edit().putString("model_provider",owners.get(which)).putString("model_id",ids.get(which)).apply();heard.setText(ids.get(which)+" connected");chooseModel();}
                });
            }).setNeutralButton("Settings",(d,w)->startActivity(new Intent(this,NativeSettingsActivity.class))).setNegativeButton("Done",null));
        });
    }
    private void voiceDetails(VoiceStore.Voice voice){
        boolean ready=VoiceStore.installed(this,voice);
        AlertDialog.Builder builder=Fonts.dialog(this).setTitle(voice.label).setMessage(voice.description+(voice.id.equals(VoiceStore.SYSTEM)?". It uses the TV's own speech engine, so replies start almost immediately.":voice.bundled?". This voice is included and works offline.":". "+voice.size()+" stored on this TV. After downloading, it works offline. Speed depends on your TV."))
            .setPositiveButton(ready?"Use & preview":"Download & use",(dialog,which)->{if(ready)useVoice(voice);else downloadVoice(voice);})
            .setNegativeButton("Back",(dialog,which)->chooseVoice());
        if(ready&&!voice.bundled)builder.setNeutralButton("Remove download",(dialog,which)->{stopSpeaking();VoiceStore.remove(this,voice);startForegroundService(new Intent(this,CompanionService.class).setAction("voice_changed"));chooseVoice();});
        Fonts.show(builder);
    }
    private void useVoice(VoiceStore.Voice voice){
        VoiceStore.select(this,voice);startForegroundService(new Intent(this,CompanionService.class).setAction("voice_test"));
        Toast.makeText(this,voice.label+" selected",Toast.LENGTH_SHORT).show();
    }
    private void downloadVoice(VoiceStore.Voice voice){
        final VoiceStore.Download[] request={null};
        final AlertDialog dialog=Fonts.show(Fonts.dialog(this).setTitle("Downloading "+voice.label).setMessage("0% · "+voice.size()).setNegativeButton("Cancel",(d,w)->{if(request[0]!=null)request[0].cancel();}));
        dialog.setCanceledOnTouchOutside(false);dialog.setOnCancelListener(d->{if(request[0]!=null)request[0].cancel();});
        request[0]=VoiceStore.install(this,voice,new VoiceStore.Listener(){
            public void progress(long bytes,long total){if(!isDestroyed()&&dialog.isShowing())dialog.setMessage((bytes*100/total)+"% · "+voice.size());}
            public void ready(){if(isDestroyed()||!dialog.isShowing())return;dialog.dismiss();useVoice(voice);}
            public void failed(String message){if(isDestroyed()||!dialog.isShowing())return;dialog.dismiss();Fonts.show(Fonts.dialog(MainActivity.this).setTitle("Voice download").setMessage(message).setPositiveButton("OK",null));}
        });
    }
    private String greetingText(){
        int h=java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);String person=CompanionService.state.optString("person","");
        return (h<5?"Good night":h<12?"Good morning":h<18?"Good afternoon":h<22?"Good evening":"Good night")+(person.isEmpty()?"":", "+person);
    }
    private void render(){if(status==null)return;JSONObject s=CompanionService.state;boolean listening=CompanionService.listening;String phase=listening?"listening":s.optString("phase","idle");face.phase=phase;face.talking=CompanionService.speaking;
        String message=listening?"Listening":CompanionService.preparingVoice?"Preparing voice":s.optString("status","Ready when you are");
        int dot=listening?Duo.SKY:phase.equals("thinking")||phase.equals("acting")?Duo.ORANGE:Duo.GREEN;
        android.text.SpannableString pill=new android.text.SpannableString("●  "+message);pill.setSpan(new android.text.style.ForegroundColorSpan(dot),0,1,0);status.setText(pill);
        boolean working=phase.equals("thinking")||phase.equals("acting");
        String answer=!listening&&!working&&CompanionService.replyDisplay.visible(s.optString("replyId",""),SystemClock.uptimeMillis(),CompanionService.speaking||CompanionService.preparingVoice)?s.optString("reply",""):"",user=s.optString("user","");
        greeting.setText(!answer.isEmpty()&&!user.isEmpty()?"You asked: “"+user+"”":greetingText());
        String text=!answer.isEmpty()?answer:listening?"I’m listening…":working?phase.equals("acting")?"On it…":"Let me think…":!s.optBoolean("configured",true)&&Api.paired(this)?"Choose Model to add your API key, then press Talk.":"What shall we do today?";
        reply.setText(text);reply.setTextSize(text.length()>140?22:text.length()>70?26:30);
        String error=CompanionService.localError.isEmpty()?s.optString("voiceError",s.optString("error","")):CompanionService.localError;
        heard.setText(listening&&!CompanionService.heard.isEmpty()?"“"+CompanionService.heard+"”":error.isEmpty()?(working&&!user.isEmpty()?"You: "+user:""):error);
        heard.setTextColor(error.isEmpty()||listening?colors.MUTED:colors.ERROR);
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
        String[] slots={"variant","hat","eyewear","neck","face"},titles={"Colour","Hat","Eyewear","Neck","Face"};java.util.ArrayList<String> labels=new java.util.ArrayList<>();
        for(int i=0;i<slots.length;i++)labels.add(titles[i]+" · "+styleName(slots[i],Api.prefs(this).getString(slots[i],i==0?"tangerine":"")));
        labels.add("Screensaver preview");
        JSONObject look=CompanionService.state.optJSONObject("look");
        if(look!=null&&look.has("label"))labels.add("Outfit suggestion · "+look.optString("label"));
        Fonts.show(Fonts.dialog(this).setTitle(Duo.name(this)+"’s style").setItems(labels.toArray(new String[0]),(d,which)->{
            if(which<slots.length)chooseStyle(slots[which],titles[which]);
            else if(which==slots.length)startActivity(new Intent(this,AmbientActivity.class));
            else suggestOutfit(look);
        }).setNegativeButton("Done",null));
    }
    /** A quiet idea inside Style: nothing changes unless the user chooses Use outfit. */
    private void suggestOutfit(JSONObject look){
        String[] slots={"hat","eyewear","neck","face"};java.util.ArrayList<String> pieces=new java.util.ArrayList<>();
        for(String slot:slots)if(look.has(slot))pieces.add(styleName(slot,look.optString(slot)));
        Fonts.show(Fonts.dialog(this).setTitle("An outfit for "+look.optString("label"))
            .setMessage(android.text.TextUtils.join(" · ",pieces)+". If you fancy a change.")
            .setPositiveButton("Use outfit",(d,w)->{
                android.content.SharedPreferences.Editor edit=Api.prefs(this).edit();
                for(String slot:slots)if(look.has(slot))edit.putString(slot,look.optString(slot));
                edit.apply();face.invalidate();chooseStyle();
            }).setNegativeButton("Keep my look",(d,w)->chooseStyle()));
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
    private void send(String text){text=text.trim();if(text.isEmpty())return;final String sent=text;CompanionService.localError="";CompanionService.replyDisplay.clear();stopSpeaking();input.setText("");heard.setText("You: "+sent);status.setText("●  Thinking");face.phase="thinking";Api.request(this,"/api/chat",Api.json("text",sent),(r,e)->{if(e!=null){CompanionService.localError=e;heard.setText(e);face.phase="idle";status.setText("●  Ready");}});}
    private boolean controllerHandlesRemote(){return CompanionService.state.optBoolean("remoteButtonEnabled")&&CompanionService.state.optBoolean("remoteButtonReady");}
    private void listen(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10);return;}
        startForegroundService(new Intent(this,CompanionService.class).setAction(CompanionService.LISTEN));
    }
    public void onRequestPermissionsResult(int r,String[] p,int[] grants){super.onRequestPermissionsResult(r,p,grants);if(r==10&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)listen();else note.setText("Microphone permission is needed for Talk. Typing remains available.");}
    public boolean onKeyDown(int key,KeyEvent e){if(key==KeyEvent.KEYCODE_SEARCH||key==KeyEvent.KEYCODE_VOICE_ASSIST||key==KeyEvent.KEYCODE_ASSIST){if(e.getRepeatCount()==0&&!controllerHandlesRemote())listen();return true;}if(key==KeyEvent.KEYCODE_MEDIA_STOP){stopSpeaking();Api.request(this,"/api/stop",new JSONObject(),null);return true;}return super.onKeyDown(key,e);}
}
