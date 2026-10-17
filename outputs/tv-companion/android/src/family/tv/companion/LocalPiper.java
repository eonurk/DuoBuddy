package family.tv.companion;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Selected Piper voice, with one reusable native engine and cancellable PCM playback. */
final class LocalPiper {
    interface Listener {void started();void finished();void interrupted();void failed(String error);}
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final SpeechTurn turns=new SpeechTurn();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>());
    private final Object outputLock=new Object();
    private final AudioManager manager;
    private Listener active;
    private final AudioManager.OnAudioFocusChangeListener focus=change->{if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){Listener old=active;stop();if(old!=null)old.interrupted();}};
    private AudioTrack output;
    private Object engine;
    private String loadedVoice="";
    private Class<?> generation,function,phonemize;
    private int sampleRate=16000;

    LocalPiper(Context c){context=c.getApplicationContext();manager=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);}
    void warm(){worker.execute(()->{try{initialize();}catch(Throwable e){Log.e("BuddyVoice","Embedded Piper warm-up failed",e);}});}
    void speak(String text,Listener listener){
        stop();active=listener;final long turn=turns.next();
        Log.i("BuddyVoice","Embedded Piper requested: turn="+turn);
        worker.getQueue().clear();
        worker.execute(()->generate(text,turn,listener));
    }
    void stop(){
        if(active!=null)Log.i("BuddyVoice","Embedded Piper stopped");
        turns.next();active=null;worker.getQueue().clear();
        synchronized(outputLock){if(output!=null)try{output.pause();output.flush();output.stop();}catch(IllegalStateException ignored){}}
        manager.abandonAudioFocus(focus);
    }
    void close(){stop();turns.close();worker.execute(()->{if(engine!=null)try{engine.getClass().getMethod("release").invoke(engine);}catch(Exception ignored){}engine=null;});worker.shutdown();}
    private void post(long turn,Runnable event){main.post(()->{if(turns.current(turn))event.run();});}
    private void initialize()throws Exception{
        VoiceStore.Voice voice=VoiceStore.selected(context);
        if(engine!=null&&voice.id.equals(loadedVoice))return;
        if(engine!=null){engine.getClass().getMethod("release").invoke(engine);engine=null;}
        Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
        long began=SystemClock.elapsedRealtime();
        phonemize=Class.forName("com.github.csukuangfj.piper.phonemize.PiperPhonemizeKt");
        phonemize.getMethod("piperPhonemizeInitialize",Context.class).invoke(null,context);
        Object config=Class.forName("com.k2fsa.sherpa.onnx.OfflineTtsConfig").getConstructor().newInstance();
        Object models=config.getClass().getMethod("getModel").invoke(config);
        Object vits=models.getClass().getMethod("getVits").invoke(models);
        String model=voice.bundled?"vits-piper-en_US-amy-low/en_US-amy-low.onnx":VoiceStore.model(context,voice).getAbsolutePath();
        String tokens=voice.bundled?"vits-piper-en_US-amy-low/tokens.txt":new java.io.File(VoiceStore.folder(context,voice),"tokens.txt").getAbsolutePath();
        vits.getClass().getMethod("setModel",String.class).invoke(vits,model);
        vits.getClass().getMethod("setTokens",String.class).invoke(vits,tokens);
        models.getClass().getMethod("setNumThreads",int.class).invoke(models,2);
        models.getClass().getMethod("setDebug",boolean.class).invoke(models,false);
        Class<?> cls=Class.forName("com.k2fsa.sherpa.onnx.OfflineTts");
        engine=cls.getConstructor(AssetManager.class,config.getClass()).newInstance(voice.bundled?context.getAssets():null,config);
        generation=Class.forName("com.k2fsa.sherpa.onnx.GenerationConfig");
        function=Class.forName("kotlin.jvm.functions.Function1");
        loadedVoice=voice.id;sampleRate=voice.rate;
        Log.i("BuddyVoice","Embedded Piper ready: voice="+loadedVoice+", loadMs="+(SystemClock.elapsedRealtime()-began));
    }
    private void generate(String text,long turn,Listener listener){
        AudioTrack track=null;
        final long began=SystemClock.elapsedRealtime();
        final long[] written={0};final boolean[] started={false};final Throwable[] playbackError={null};
        try{
            if(!turns.current(turn))return;
            initialize();if(!turns.current(turn))return;
            Log.i("BuddyVoice","Embedded Piper generating: turn="+turn);
            Object settings=generation.getConstructor().newInstance();
            generation.getMethod("setSid",int.class).invoke(settings,0);
            generation.getMethod("setSpeed",float.class).invoke(settings,1.0f);
            Object phonemes=phonemize.getMethod("piperPhonemizeText",String.class,String.class).invoke(null,text,"en-us");
            try{
                int count=(Integer)phonemes.getClass().getMethod("getNumSentences").invoke(phonemes);
                int[][] sentences=new int[count][];
                for(int i=0;i<count;i++)sentences[i]=(int[])phonemes.getClass().getMethod("getPhonemes",int.class).invoke(phonemes,i);
                generation.getMethod("setPhonemeCodepoints",int[][].class).invoke(settings,(Object)sentences);
            }finally{phonemes.getClass().getMethod("close").invoke(phonemes);}
            // PCM float mono frames are four bytes, including at 22,050 Hz.
            int buffer=(Math.max(sampleRate,AudioTrack.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_FLOAT))+3)/4*4;
            track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(buffer).setTransferMode(AudioTrack.MODE_STREAM).build();
            final AudioTrack current=track;
            synchronized(outputLock){if(!turns.current(turn))return;output=track;}
            Object callback=new PiperCallback(samples->{
                if(!turns.current(turn))return 0;
                // Never leave a pending Java exception inside the native callback bridge.
                try{write(samples,current,turn,listener,began,written,started);}
                catch(Throwable error){playbackError[0]=error;return 0;}
                return turns.current(turn)?1:0;
            });
            Object audio=engine.getClass().getMethod("generateWithConfigAndCallback",String.class,generation,function).invoke(engine,text,settings,callback);
            Log.i("BuddyVoice","Embedded Piper generated: turn="+turn+", current="+turns.current(turn)+", streamedSamples="+written[0]);
            if(!turns.current(turn))return;
            if(playbackError[0]!=null)throw new IllegalStateException("Speech playback failed",playbackError[0]);
            // Some wrappers return valid audio without invoking their streaming callback.
            if(written[0]==0)write((float[])audio.getClass().getMethod("getSamples").invoke(audio),track,turn,listener,began,written,started);
            if(written[0]==0)throw new IllegalStateException("Piper produced no audio");
            long deadline=SystemClock.elapsedRealtime()+written[0]*1000/sampleRate+5000;
            while(turns.current(turn)&&Integer.toUnsignedLong(track.getPlaybackHeadPosition())<written[0]){
                if(SystemClock.elapsedRealtime()>deadline)throw new IllegalStateException("Speech playback timed out");
                Thread.sleep(20);
            }
            if(turns.current(turn)){Log.i("BuddyVoice","Embedded Piper completed: samples="+written[0]+", elapsedMs="+(SystemClock.elapsedRealtime()-began));post(turn,listener::finished);}
        }catch(Throwable error){
            Throwable cause=error instanceof InvocationTargetException&&error.getCause()!=null?error.getCause():error;
            if(turns.current(turn)){Log.e("BuddyVoice","Embedded Piper failed",cause);post(turn,()->listener.failed("TV voice: "+cause.getMessage()));}
        }finally{
            synchronized(outputLock){if(output==track)output=null;if(track!=null){try{track.stop();}catch(IllegalStateException ignored){}track.release();}}
            // An obsolete worker must not abandon a newer turn's focus.
            if(turns.current(turn))manager.abandonAudioFocus(focus);
        }
    }
    private void write(float[] samples,AudioTrack track,long turn,Listener listener,long began,long[] written,boolean[] started){
        if(samples.length==0||!turns.current(turn))return;
        if(!started[0]){
            synchronized(outputLock){
                if(!turns.current(turn))return;
                int granted=manager.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
                if(granted!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)throw new IllegalStateException("Audio is busy. Try again.");
                track.play();started[0]=true;
            }
            Log.i("BuddyVoice","Embedded Piper audio started: firstAudioMs="+(SystemClock.elapsedRealtime()-began));post(turn,listener::started);
        }
        int offset=0;long lastWrite=SystemClock.elapsedRealtime();
        while(offset<samples.length&&turns.current(turn)){
            // Non-blocking writes keep Stop responsive even when the audio output stalls.
            int n=track.write(samples,offset,Math.min(4096,samples.length-offset),AudioTrack.WRITE_NON_BLOCKING);
            if(n<0){if(!turns.current(turn))return;throw new IllegalStateException("AudioTrack write failed: "+n);}
            offset+=n;written[0]+=n;
            if(n>0)lastWrite=SystemClock.elapsedRealtime();
            else if(SystemClock.elapsedRealtime()-lastWrite>5000)throw new IllegalStateException("Audio output stopped responding");
            if(n==0)try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
        }
    }
}
