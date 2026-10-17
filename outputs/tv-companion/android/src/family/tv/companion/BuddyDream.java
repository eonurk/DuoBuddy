package family.tv.companion;

import android.service.dreams.DreamService;

/** Buddy's dashboard as the TV's own screensaver (Settings → Device Preferences → Screen saver). */
public final class BuddyDream extends DreamService {
    private AmbientView view;
    public void onAttachedToWindow(){super.onAttachedToWindow();setInteractive(false);setFullscreen(true);view=new AmbientView(this);setContentView(view);}
    public void onDreamingStarted(){super.onDreamingStarted();view.start();}
    public void onDreamingStopped(){view.stop();super.onDreamingStopped();}
}
