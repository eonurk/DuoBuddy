package co.duobook.voicelab;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;

/** Calls the publisher's bundled JNI API directly, preserving its returned audio. */
final class DirectVoice {
    private final Object tts;
    private final Class<?> generation;
    private final Class<?> function;
    private final Class<?> phonemize;
    private final int speaker;
    DirectVoice(Context context, String model, int sid, int threads) throws Exception {
        speaker = sid;
        phonemize = Class.forName("com.github.csukuangfj.piper.phonemize.PiperPhonemizeKt");
        phonemize.getMethod("piperPhonemizeInitialize", Context.class).invoke(null, context);
        String dir = model.equals("nano-fp32") ? "kitten-nano-en-v0_8-fp32" : model.equals("nano") ? "kitten-nano-en-v0_8-int8" : model.equals("micro") ? "kitten-micro-en-v0_8" : "vits-piper-en_US-amy-low";
        String modelFile = model.equals("nano-fp32") ? "model.fp32.onnx" : model.equals("nano") ? "model.int8.onnx" : model.equals("micro") ? "model.onnx" : "en_US-amy-low.onnx";
        Object config = Class.forName("com.k2fsa.sherpa.onnx.OfflineTtsConfig").getConstructor().newInstance();
        Object models = config.getClass().getMethod("getModel").invoke(config);
        Object voice = models.getClass().getMethod(model.equals("amy") ? "getVits" : "getKitten").invoke(models);
        set(voice, "Model", dir + "/" + modelFile);
        set(voice, "Tokens", dir + "/tokens.txt");
        if (!model.equals("amy")) set(voice, "Voices", dir + "/voices.bin");
        String data = dir + "/espeak-ng-data";
        if (context.getAssets().list(data).length > 0) {
            File destination = new File(context.getExternalFilesDir(null), "phonemes");
            copy(context.getAssets(), data, new File(destination, data));
            set(voice, "DataDir", new File(destination, data).getAbsolutePath());
        }
        models.getClass().getMethod("setNumThreads", int.class).invoke(models, threads);
        models.getClass().getMethod("setDebug", boolean.class).invoke(models, false);
        Class<?> cls = Class.forName("com.k2fsa.sherpa.onnx.OfflineTts");
        tts = cls.getConstructor(AssetManager.class, config.getClass()).newInstance(context.getAssets(), config);
        generation = Class.forName("com.k2fsa.sherpa.onnx.GenerationConfig");
        function = Class.forName("kotlin.jvm.functions.Function1");
    }
    interface FirstAudio {void received();}
    void generate(String text, File output, FirstAudio first) throws Exception {
        Object settings = generation.getConstructor().newInstance();
        generation.getMethod("setSid", int.class).invoke(settings, speaker);
        generation.getMethod("setSpeed", float.class).invoke(settings, 1.0f);
        Object phonemes = phonemize.getMethod("piperPhonemizeText", String.class, String.class).invoke(null, text, "en-us");
        try {
            int count = (Integer)phonemes.getClass().getMethod("getNumSentences").invoke(phonemes);
            int[][] sentences = new int[count][];
            for (int i = 0; i < count; i++) sentences[i] = (int[])phonemes.getClass().getMethod("getPhonemes", int.class).invoke(phonemes, i);
            generation.getMethod("setPhonemeCodepoints", int[][].class).invoke(settings, (Object)sentences);
        } finally {phonemes.getClass().getMethod("close").invoke(phonemes);}
        Object callback = Proxy.newProxyInstance(function.getClassLoader(), new Class<?>[]{function}, (proxy, method, args) -> {
            if (method.getName().equals("invoke")) {if (args != null && args[0] instanceof float[] && ((float[])args[0]).length > 0) first.received(); return 1;}
            if (method.getName().equals("toString")) return "VoiceLabCallback";
            if (method.getName().equals("equals")) return proxy == args[0];
            return 1;
        });
        Object audio = tts.getClass().getMethod("generateWithConfigAndCallback", String.class, generation, function).invoke(tts, text, settings, callback);
        boolean saved = (Boolean)audio.getClass().getMethod("save", String.class).invoke(audio, output.getAbsolutePath());
        if (!saved) throw new java.io.IOException("Native engine returned no audio");
    }
    void close() {try {tts.getClass().getMethod("release").invoke(tts);} catch (Exception ignored) {}}
    private static void set(Object target, String property, String value) throws Exception {target.getClass().getMethod("set" + property, String.class).invoke(target, value);}
    private static void copy(AssetManager assets, String source, File destination) throws Exception {
        String[] children = assets.list(source);
        if (children.length > 0) {destination.mkdirs(); for (String child : children) copy(assets, source + "/" + child, new File(destination, child));}
        else if (!destination.exists()) {
            destination.getParentFile().mkdirs();
            try (InputStream in = assets.open(source); FileOutputStream out = new FileOutputStream(destination)) {byte[] buffer = new byte[16384]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);}
        }
    }
}
