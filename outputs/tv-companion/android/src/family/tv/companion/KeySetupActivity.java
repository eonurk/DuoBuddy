package family.tv.companion;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.concurrent.*;

/** One-time phone entry, plus a native keyboard fallback. Secrets never appear in a QR or URL. */
public final class KeySetupActivity extends Activity {
    private PhoneSetupServer setup;private volatile CountDownLatch pending;private volatile boolean destroyed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private TextView status,certificate;private ImageView qr;private String provider,model;
    private int dp(float value){return Duo.dp(this,value);}
    public void onCreate(Bundle saved){
        setTheme(Duo.theme(this));super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().getDecorView().setSystemUiVisibility(5894);
        startForegroundService(new Intent(this,CompanionService.class).setAction("pause_assistant"));
        provider=getIntent().getStringExtra("provider");if(provider==null)provider=AiProtocol.OPENAI;model=getIntent().getStringExtra("model");if(model==null)model=AiProtocol.defaultModel(provider);
        try{AiProtocol.validateSelection(provider,model);}catch(Exception e){finish();return;}
        Duo.Palette colors=Duo.palette(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(48),dp(32),dp(48),dp(28));root.setBackgroundColor(colors.CANVAS);setContentView(root);
        root.addView(Duo.text(this,"Connect your AI",30,colors.INK,700));
        TextView sub=Duo.text(this,AiProtocol.label(provider)+" · "+model,17,colors.SECOND,600);LinearLayout.LayoutParams subp=new LinearLayout.LayoutParams(-1,-2);subp.topMargin=dp(10);root.addView(sub,subp);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,0,1);rp.topMargin=dp(18);root.addView(row,rp);
        qr=new ImageView(this);qr.setBackgroundColor(0xffffffff);qr.setPadding(dp(8),dp(8),dp(8),dp(8));row.addView(qr,new LinearLayout.LayoutParams(dp(218),dp(218)));
        LinearLayout instructions=new LinearLayout(this);instructions.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-2,1);ip.leftMargin=dp(30);row.addView(instructions,ip);
        TextView steps=Duo.text(this,"1. Connect your phone to the same Wi-Fi.\n2. Scan the QR and paste your API key.\n3. Confirm the request on this TV.",19,colors.INK,600);steps.setLineSpacing(dp(6),1);instructions.addView(steps);
        status=Duo.text(this,"Preparing secure setup…",13,colors.SECOND,500);status.setTextIsSelectable(true);LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-1,-2);st.topMargin=dp(14);instructions.addView(status,st);
        certificate=Duo.text(this,"",11,colors.SECOND,500);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(8);instructions.addView(certificate,cp);
        TextView note=Duo.text(this,"Your browser may ask you to trust this TV’s local certificate. Compare its SHA-256 fingerprint above. Setup expires after 3 minutes. Your key stays encrypted on the TV; no Mac or phone is needed afterward.",13,colors.SECOND,500);root.addView(note,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=new LinearLayout(this);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(52));ap.topMargin=dp(10);root.addView(actions,ap);
        actions.addView(Duo.quiet(this,"keyboard","Enter key on TV",()->manual()),new LinearLayout.LayoutParams(-2,-1));actions.addView(Duo.quiet(this,"model","Done",()->finish()),new LinearLayout.LayoutParams(-2,-1));actions.getChildAt(1).requestFocus();
        Api.IO.execute(()->{
            try{
                PhoneSetupServer server=new PhoneSetupServer(this,provider,model,this::approve,()->main.post(()->{if(!destroyed){status.setText("Connected on this TV. Choose Done to start talking.");Toast.makeText(this,"Connected on this TV",Toast.LENGTH_LONG).show();}}));setup=server;String url=server.start();
                BitMatrix matrix=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,480,480);int[] pixels=new int[480*480];for(int y=0;y<480;y++)for(int x=0;x<480;x++)pixels[y*480+x]=matrix.get(x,y)?0xff000000:0xffffffff;
                Bitmap image=Bitmap.createBitmap(pixels,480,480,Bitmap.Config.ARGB_8888);
                main.post(()->{if(destroyed){server.close();return;}qr.setImageBitmap(image);status.setText(url);certificate.setText("Certificate SHA-256:\n"+server.fingerprint());});
                main.postDelayed(()->{if(!destroyed)status.setText("Setup expired. Close and reopen this screen to create a fresh QR code.");},180000);
            }catch(Exception e){main.post(()->{if(!destroyed)status.setText(e.getMessage()==null?"Phone setup could not start. Use Enter key on TV.":e.getMessage());});}
        });
    }
    private boolean approve(String selected,String id)throws Exception{
        CountDownLatch wait=new CountDownLatch(1);pending=wait;final boolean[] accepted={false};
        main.post(()->{
            if(destroyed){wait.countDown();return;}
            AlertDialog dialog=Fonts.show(Fonts.dialog(this).setTitle("Save key from your phone?")
                .setMessage("Save an "+AiProtocol.label(selected)+" API key on this TV and use "+id+"? The key is sent only to that provider.")
                .setPositiveButton("Save & connect",(d,w)->{accepted[0]=true;wait.countDown();})
                .setNegativeButton("Cancel",(d,w)->wait.countDown()));
            dialog.setOnCancelListener(d->wait.countDown());
        });
        boolean done=wait.await(60,TimeUnit.SECONDS);pending=null;
        if(done&&accepted[0])main.post(()->{if(!destroyed)status.setText("Checking key and model…");});
        return done&&accepted[0]&&!destroyed;
    }
    private void manual(){
        EditText key=new EditText(this);key.setHint("Paste your "+AiProtocol.label(provider)+" API key");key.setSingleLine(true);key.setSaveEnabled(false);key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);key.setTextSize(17);key.setPadding(dp(18),dp(12),dp(18),dp(12));
        AlertDialog dialog=Fonts.show(Fonts.dialog(this).setTitle(AiProtocol.label(provider)+" API key").setView(key).setPositiveButton("Connect",null).setNegativeButton("Cancel",null));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String secret=key.getText().toString().trim();key.setText("");dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            Api.IO.execute(()->{try{LocalController.get(this).configure(provider,model,secret);main.post(()->{if(!destroyed){dialog.dismiss();Toast.makeText(this,"Connected on this TV",Toast.LENGTH_LONG).show();finish();}});}catch(Exception e){main.post(()->{if(!destroyed){dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);key.setError(e.getMessage());}});}});
        });
    }
    protected void onDestroy(){destroyed=true;if(pending!=null)pending.countDown();if(setup!=null)setup.close();main.removeCallbacksAndMessages(null);super.onDestroy();}
}
