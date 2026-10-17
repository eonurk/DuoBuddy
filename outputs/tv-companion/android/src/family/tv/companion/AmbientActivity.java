package family.tv.companion;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

/** The screensaver on demand ("Buddy, screensaver mode"); any remote button closes it. */
public final class AmbientActivity extends Activity {
    private AmbientView view;
    protected void onCreate(Bundle b){super.onCreate(b);view=new AmbientView(this);setContentView(view);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
    protected void onResume(){super.onResume();view.start();}
    protected void onPause(){view.stop();super.onPause();}
    public boolean onKeyDown(int key,KeyEvent e){if(key==KeyEvent.KEYCODE_VOLUME_UP||key==KeyEvent.KEYCODE_VOLUME_DOWN||key==KeyEvent.KEYCODE_VOLUME_MUTE)return super.onKeyDown(key,e);finish();return true;}
}
