package family.tv.companion;

/** JNI looks up invoke(float[]) -> Integer; a generic reflection Proxy cannot supply it. */
public final class PiperCallback implements kotlin.jvm.functions.Function1<float[],Integer> {
    interface Sink {int audio(float[] samples);}
    private final Sink sink;
    PiperCallback(Sink sink){this.sink=sink;}
    @Override public Integer invoke(float[] samples){return sink.audio(samples);}
}
