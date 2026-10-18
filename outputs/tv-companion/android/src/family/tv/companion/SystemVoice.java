package family.tv.companion;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.Locale;
import java.util.UUID;

/** The TV's own text-to-speech engine (Google speech on most TVs). It starts talking almost at once, where Piper runs slower than real time on weak CPUs. */
final class SystemVoice {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AudioManager manager;
    private final TextToSpeech tts;
    private boolean initialized,ready;
    private String utterance,pendingText;
    private LocalPiper.Listener listener;
    private final AudioManager.OnAudioFocusChangeListener focus=change->{if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){LocalPiper.Listener old=listener;stop();if(old!=null)old.interrupted();}};

    SystemVoice(Context context){
        manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        tts=new TextToSpeech(context.getApplicationContext(),status->main.post(()->ready(status==TextToSpeech.SUCCESS)));
    }
    private void ready(boolean ok){
        initialized=true;
        if(ok){
            int language=tts.setLanguage(Locale.US);ok=language!=TextToSpeech.LANG_MISSING_DATA&&language!=TextToSpeech.LANG_NOT_SUPPORTED;
            tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                public void onStart(String id){main.post(()->{if(id.equals(utterance)&&listener!=null)listener.started();});}
                public void onDone(String id){main.post(()->{if(!id.equals(utterance))return;LocalPiper.Listener done=listener;release();if(done!=null)done.finished();});}
                public void onError(String id){onError(id,TextToSpeech.ERROR);}
                public void onError(String id,int code){main.post(()->{if(!id.equals(utterance))return;LocalPiper.Listener failed=listener;release();if(failed!=null)failed.failed("The TV voice could not speak ("+code+").");});}
            });
        }
        ready=ok;Log.i("BuddyVoice","TV voice "+(ok?"ready: "+tts.getDefaultEngine():"unavailable"));
        String text=pendingText;LocalPiper.Listener waiting=listener;pendingText=null;
        if(text!=null&&waiting!=null){if(ready)start(text);else{release();waiting.failed("The TV voice is unavailable. Choose another voice in Voice.");}}
    }
    /** Main thread only. Replaces anything still being said. */
    void speak(String text,LocalPiper.Listener l){
        stop();listener=l;utterance=UUID.randomUUID().toString();
        if(!initialized){pendingText=text;return;}
        if(!ready){release();l.failed("The TV voice is unavailable. Choose another voice in Voice.");return;}
        start(text);
    }
    private void start(String text){
        if(manager.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){LocalPiper.Listener busy=listener;release();if(busy!=null)busy.failed("Audio is busy. Try again.");return;}
        Bundle params=new Bundle();params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM,AudioManager.STREAM_MUSIC);
        if(tts.speak(text,TextToSpeech.QUEUE_FLUSH,params,utterance)!=TextToSpeech.SUCCESS){LocalPiper.Listener failed=listener;release();if(failed!=null)failed.failed("The TV voice could not speak.");}
    }
    /** Silences the current utterance; its listener gets no further callbacks. */
    void stop(){boolean active=utterance!=null;release();if(active&&initialized&&ready)tts.stop();}
    private void release(){utterance=null;pendingText=null;listener=null;manager.abandonAudioFocus(focus);}
    void close(){stop();tts.shutdown();}
}
