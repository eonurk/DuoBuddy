package co.duobook.voicelab;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.Locale;

/** Isolated offline benchmark. No microphone, network, storage or system permissions. */
public final class VoiceLabActivity extends Activity {
    private static final String ENGINE = "com.k2fsa.sherpa.onnx.tts.engine";
    private static final String SHORT = "Hi! I'm your DuoBuddy. Shall we find something good to watch?";
    private static final String LONG = "Let's have a quiet film night. I can help you find a comedy, turn the volume down, or set a timer for the popcorn. If you already know what you want to watch, just tell me the title. I'll take care of the rest.";
    private static final String NUMBERS = "The volume is now thirty percent. Your ten minute timer is running. It's Saturday evening, and we have three films to choose from.";
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ready, running, finished;
    private String model = "nano", sample = "short", id = "manual", text;
    private long openedAt, initMs, synthesisAt, firstAudioMs = -1, engineStartMs = -1;
    private File audio;
    private TextView status;
    private MediaPlayer player;
    private boolean autoplay;
    private boolean directMode;
    private DirectVoice direct;
    private int speaker, threads;
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Runnable timeout = () -> complete("Timed out after 120 seconds");

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        openedAt = SystemClock.elapsedRealtime();
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(48, 24, 48, 24);
        TextView title = new TextView(this); title.setText("DuoBuddy · offline voice test"); title.setTextSize(28); layout.addView(title);
        status = new TextView(this); status.setTextSize(19); status.setPadding(0, 16, 0, 16); layout.addView(status);
        TextView explanation = new TextView(this); explanation.setText("The selected speech engine runs on this Mi Box. No cloud voice, microphone access, or change to your default speech engine."); layout.addView(explanation);
        Button play = button(layout, "Play last sample", () -> playAudio());
        button(layout, "Try short sentence again", () -> begin("short", "manual-" + System.currentTimeMillis(), true));
        button(layout, "Try a longer reply", () -> begin("long", "manual-" + System.currentTimeMillis(), true));
        button(layout, "Back to DuoBuddy", () -> {Intent next = getPackageManager().getLaunchIntentForPackage("family.tv.companion"); if (next != null) startActivity(next); finish();});
        ScrollView scroll = new ScrollView(this); scroll.addView(layout); setContentView(scroll); play.requestFocus();
        accept(getIntent());
    }

    private Button button(LinearLayout layout, String label, Runnable action) {Button b = new Button(this); b.setText(label); b.setOnClickListener(v -> action.run()); layout.addView(b); return b;}
    @Override public void onNewIntent(Intent intent) {super.onNewIntent(intent); setIntent(intent); accept(intent);}

    private void accept(Intent intent) {
        if (running) return;
        directMode = intent.getBooleanExtra("direct", false);
        speaker = intent.getIntExtra("speaker", 0);
        threads = Math.max(1, Math.min(4, intent.getIntExtra("threads", 2)));
        try (java.io.InputStream metadata = getAssets().open("voice-lab.json")) {
            byte[] data = new byte[256]; int size = metadata.read(data);
            JSONObject bundled = new JSONObject(new String(data, 0, size, "UTF-8"));
            model = bundled.getString("model"); directMode = true;
            if (!intent.hasExtra("speaker")) speaker = model.equals("amy") ? 0 : 1;
        } catch (Exception ignored) {}
        String selected = intent.getStringExtra("model");
        if (selected != null && selected.matches("nano|nano-fp32|micro|amy")) model = selected;
        String requested = intent.getStringExtra("sample");
        String requestId = intent.getStringExtra("id");
        begin(requested == null ? "short" : requested, requestId == null ? "manual-" + System.currentTimeMillis() : requestId, intent.getBooleanExtra("autoplay", false));
    }

    private void begin(String selected, String requestId, boolean play) {
        if (running || !requestId.matches("[A-Za-z0-9_-]{1,80}")) return;
        if (!selected.matches("short|long|numbers")) return;
        stopPlayback();
        sample = selected; id = requestId; autoplay = play; running = true; finished = false;
        text = sample.equals("long") ? LONG : sample.equals("numbers") ? NUMBERS : SHORT;
        audio = new File(getExternalFilesDir(null), id + ".wav");
        firstAudioMs = -1; engineStartMs = -1; synthesisAt = 0;
        main.postDelayed(timeout, 120000);
        status.setText(model + " · " + sample + "\nLoading offline voice…\n\n" + text);
        if (directMode) {
            worker.execute(() -> {
                try {
                    long init = SystemClock.elapsedRealtime();
                    initMs = 0;
                    if (direct == null) {direct = new DirectVoice(this, model, speaker, threads); initMs = SystemClock.elapsedRealtime() - init;}
                    main.post(() -> status.setText(model + " · " + sample + "\nGenerating with the native engine…\n\n" + text));
                    synthesisAt = SystemClock.elapsedRealtime();
                    direct.generate(text, audio, () -> {if (firstAudioMs < 0) firstAudioMs = SystemClock.elapsedRealtime() - synthesisAt;});
                    main.post(() -> complete(null));
                } catch (Throwable failure) {
                    Log.e("DuoVoiceLab", "native synthesis", failure);
                    Throwable reason = failure; while (reason.getCause() != null) reason = reason.getCause();
                    final String message = reason.toString(); main.post(() -> complete(message));
                }
            });
            return;
        }
        try {getPackageManager().getPackageInfo(ENGINE, 0);} catch (PackageManager.NameNotFoundException e) {complete("Install the selected Sherpa-ONNX test engine first."); return;}
        if (ready) {initMs = 0; synthesize(); return;}
        openedAt = SystemClock.elapsedRealtime();
        tts = new TextToSpeech(this, code -> main.post(() -> {
            if (finished || isFinishing()) return;
            initMs = SystemClock.elapsedRealtime() - openedAt;
            if (code != TextToSpeech.SUCCESS) {complete("Offline speech engine failed to initialize (" + code + ")"); return;}
            ready = true;
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {if (id.equals(utteranceId)) engineStartMs = SystemClock.elapsedRealtime() - synthesisAt;}
                @Override public void onAudioAvailable(String utteranceId, byte[] bytes) {if (id.equals(utteranceId) && bytes.length > 0 && firstAudioMs < 0) firstAudioMs = SystemClock.elapsedRealtime() - synthesisAt;}
                @Override public void onDone(String utteranceId) {if (id.equals(utteranceId)) main.post(() -> complete(null));}
                @Override public void onError(String utteranceId) {if (id.equals(utteranceId)) main.post(() -> complete("Synthesis failed"));}
                @Override public void onError(String utteranceId, int code) {if (id.equals(utteranceId)) main.post(() -> complete("Synthesis failed (" + code + ")"));}
            });
            int language = tts.setLanguage(Locale.US);
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {complete("English is unavailable in the selected engine"); return;}
            tts.setSpeechRate(1.0f);
            synthesize();
        }), ENGINE);
    }

    private void synthesize() {
        status.setText(model + " · " + sample + "\nGenerating locally on the Mi Box…\n\n" + text);
        synthesisAt = SystemClock.elapsedRealtime();
        Bundle params = new Bundle();
        int ret = tts.synthesizeToFile(text, params, audio, id);
        if (ret != TextToSpeech.SUCCESS) complete("The engine rejected the synthesis request");
    }

    private void complete(String error) {
        if (finished || !running) return;
        finished = true; running = false; main.removeCallbacks(timeout);
        long generatedMs = synthesisAt > 0 ? SystemClock.elapsedRealtime() - synthesisAt : 0;
        try {
            double duration = audio != null && audio.exists() ? wavDuration(audio) : 0;
            if (error == null && duration <= 0) error = "The engine produced no playable WAV audio";
            JSONObject result = new JSONObject();
            result.put("id", id); result.put("model", model); result.put("sample", sample); result.put("text", text);
            result.put("engine", ENGINE); result.put("initMs", initMs); result.put("synthesisMs", generatedMs);
            result.put("directNative", directMode); result.put("speaker", speaker); result.put("threads", threads);
            result.put("firstAudioMs", firstAudioMs); result.put("engineStartMs", engineStartMs);
            result.put("audioSeconds", duration); result.put("realTimeFactor", duration > 0 ? generatedMs / (duration * 1000.0) : JSONObject.NULL);
            result.put("error", error == null ? JSONObject.NULL : error); result.put("audioBytes", audio != null ? audio.length() : 0);
            // Match the active Android binding rather than silently accepting a fallback voice.
            if (directMode) result.put("boundEngine", "bundled Sherpa-ONNX JNI (no Android TTS service)");
            else try {result.put("boundEngine", TextToSpeech.class.getMethod("getCurrentEngine").invoke(tts));} catch (Exception ignored) {result.put("boundEngine", "verify with service binding/logcat");}
            File target = new File(getExternalFilesDir(null), id + ".json");
            File temporary = new File(getExternalFilesDir(null), id + ".json.tmp");
            try (FileOutputStream out = new FileOutputStream(temporary)) {out.write(result.toString(2).getBytes("UTF-8"));}
            if (!temporary.renameTo(target)) throw new java.io.IOException("Could not save results");
            Log.i("DuoVoiceLab", result.toString());
            status.setText(error != null ? model + " failed: " + error : String.format(Locale.US, "%s · %s\nVoice loaded: %.2f s · Generated: %.2f s\nAudio length: %.2f s · Generation/audio: %.2f\n\n%s", model, sample, initMs / 1000.0, generatedMs / 1000.0, duration, generatedMs / (duration * 1000), text));
            if (error == null && autoplay) playAudio();
        } catch (Exception e) {status.setText("Could not save results: " + e.getMessage()); Log.e("DuoVoiceLab", "results", e);}
    }

    private static long uint32(RandomAccessFile file) throws Exception {return Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));}
    private static double wavDuration(File path) throws Exception {
        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
            if (file.length() < 44 || file.readInt() != 0x52494646) return 0;
            file.seek(8); if (file.readInt() != 0x57415645) return 0;
            long rate = 0;
            while (file.getFilePointer() + 8 <= file.length()) {
                int chunk = file.readInt(); long size = uint32(file), start = file.getFilePointer();
                if (chunk == 0x666d7420 && size >= 16) {file.skipBytes(8); rate = uint32(file);}
                if (chunk == 0x64617461) return rate > 0 ? Math.min(size, file.length() - start) / (double) rate : 0;
                file.seek(Math.min(file.length(), start + size + (size % 2)));
            }
            return 0;
        }
    }

    private void stopPlayback() {if (player != null) {player.release(); player = null;}}
    private void playAudio() {
        if (running || audio == null || !audio.exists() || audio.length() <= 44) return;
        stopPlayback();
        try {player = new MediaPlayer(); player.setDataSource(audio.getAbsolutePath()); player.setOnCompletionListener(p -> stopPlayback()); player.prepare(); player.start();}
        catch (Exception e) {status.setText("Audio playback failed: " + e.getMessage()); stopPlayback();}
    }
    @Override public void onDestroy() {main.removeCallbacks(timeout); stopPlayback(); if (tts != null) {tts.stop(); tts.shutdown();} worker.execute(() -> {if (direct != null) direct.close();}); worker.shutdown(); super.onDestroy();}
}
