package family.tv.companion;

public final class SpeechTurnTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        check(PiperCallback.class.getMethod("invoke",float[].class).getReturnType()==Integer.class,"JNI requires the concrete float[] -> Integer signature, not an erased Object -> Object Proxy");
        SpeechTurn speech=new SpeechTurn();
        long first=speech.next();check(speech.current(first),"new speech may play");
        long replacement=speech.next();check(!speech.current(first),"replacing speech invalidates native callbacks and queued UI events");
        check(speech.current(replacement),"replacement remains current");
        speech.next();check(!speech.current(replacement),"Stop during model loading prevents later playback");
        PiperCallback callback=new PiperCallback(samples->speech.current(replacement)?1:0);
        check(callback.invoke(new float[]{0.1f})==0,"a stopped native callback tells Piper to cancel");
        long finalTurn=speech.next();
        Thread stale=new Thread(()->{check(!speech.current(first),"worker must observe cancellation across threads");});
        stale.start();stale.join();
        speech.close();check(!speech.current(finalTurn),"destroying the service invalidates pending completion");
        check(!speech.current(speech.next()),"a closed engine can never become current again");
        System.out.println("Speech cancellation regression checks passed.");
    }
}
