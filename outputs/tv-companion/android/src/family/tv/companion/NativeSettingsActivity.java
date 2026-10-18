package family.tv.companion;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public final class NativeSettingsActivity extends Activity {
    private int dp(float v){return Duo.dp(this,v);}
    public void onCreate(Bundle b){setTheme(Duo.theme(this));super.onCreate(b);getWindow().getDecorView().setSystemUiVisibility(5894);startForegroundService(new Intent(this,CompanionService.class).setAction("pause_assistant"));build();}
    protected void onResume(){super.onResume();build();}
    private void build(){
        Duo.Palette colors=Duo.palette(this);LocalController runtime=LocalController.get(this);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(56),dp(28),dp(56),dp(24));root.setBackgroundColor(colors.CANVAS);scroll.addView(root);setContentView(scroll);
        root.addView(Duo.text(this,"DuoBuddy settings",28,colors.INK,700));root.addView(Duo.text(this,"Runs on this TV · no Mac controller required",15,colors.SECOND,500));
        add(root,"model","OpenAI API key"+(runtime.vault.has(AiProtocol.OPENAI)?" · saved":" · not set"),()->connect(AiProtocol.OPENAI));
        add(root,"model","Google Gemini API key"+(runtime.vault.has(AiProtocol.GEMINI)?" · saved":" · not set"),()->connect(AiProtocol.GEMINI));
        add(root,"apps","TV control · "+(TvAccess.enabled()?"enabled":"enable Accessibility"),()->open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        add(root,"mic","Default voice assistant",()->open(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)));
        add(root,"style","Captions over other apps · "+(Settings.canDrawOverlays(this)?"allowed":"allow"),()->open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));
        toggle(root,"Conversation mode: listen after a question","conversation",true);
        toggle(root,"Web search for OpenAI models","webSearch",true);
        toggle(root,"Keep Buddy visible while browsing","overlay",false);
        TextView note=Duo.text(this,"TV control is optional. It lets DuoBuddy read accessible screen text and navigate other apps. Protected video and apps without accessibility support cannot be read. Some remotes keep their microphone button assigned to the system assistant.",13,colors.SECOND,500);root.addView(note);
        add(root,"model","Remove a saved API key",()->removeKey());
        add(root,"apps","Done",()->finish());
    }
    private void add(LinearLayout root,String icon,String text,Runnable click){View row=Duo.row(this,icon,text,click);root.addView(row,new LinearLayout.LayoutParams(-1,dp(42)));}
    private void toggle(LinearLayout root,String text,String name,boolean fallback){CheckBox toggle=new CheckBox(this);toggle.setText(text);toggle.setTextSize(15);toggle.setTextColor(Duo.palette(this).BODY);toggle.setTypeface(Fonts.get(this,600));toggle.setChecked(LocalController.get(this).setting(name,fallback));toggle.setOnCheckedChangeListener((b,on)->LocalController.get(this).setSetting(name,on));root.addView(toggle,new LinearLayout.LayoutParams(-1,dp(40)));}
    private void connect(String provider){startActivity(new Intent(this,KeySetupActivity.class).putExtra("provider",provider).putExtra("model",AiProtocol.defaultModel(provider)));}
    private void open(Intent intent){try{startActivity(intent);}catch(Exception e){Fonts.show(Fonts.dialog(this).setTitle("Open TV settings").setMessage("This TV does not expose that settings shortcut. Open Android Settings and look for Accessibility, Apps, or Default assistant.").setPositiveButton("OK",null));}}
    private void removeKey(){Fonts.show(Fonts.dialog(this).setTitle("Remove saved API key").setItems(new String[]{"OpenAI","Google Gemini"},(d,which)->{String provider=which==0?AiProtocol.OPENAI:AiProtocol.GEMINI;Fonts.show(Fonts.dialog(this).setTitle("Remove "+AiProtocol.label(provider)+" key?").setMessage("You will need to enter this provider’s key again to use it.").setPositiveButton("Remove",(dialog,w)->{LocalController.get(this).forgetKey(provider);build();}).setNegativeButton("Keep",null));}).setNegativeButton("Cancel",null));}
}
