package family.tv.companion;
import android.content.*;
import android.os.Bundle;
import android.service.voice.*;
public final class BuddySessionService extends VoiceInteractionSessionService {
    public VoiceInteractionSession onNewSession(Bundle args){return new VoiceInteractionSession(this){
        public void onShow(Bundle args,int flags){super.onShow(args,flags);setUiEnabled(false);
            getContext().startForegroundService(new Intent(getContext(),CompanionService.class).setAction(CompanionService.LISTEN));
            new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{hide();finish();});
        }
    };}
}
