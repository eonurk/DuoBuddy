package family.tv.companion;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
public final class ClipboardActivity extends Activity {
    public void onCreate(Bundle b){super.onCreate(b);String text=getIntent().getStringExtra("text");if(text!=null&&text.length()<=200)((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("TV text",text));finish();overridePendingTransition(0,0);}
}
