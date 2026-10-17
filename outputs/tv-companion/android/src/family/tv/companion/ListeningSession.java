package family.tv.companion;

/** Bounded listening, preserving an utterance across premature recognizer endpoints. */
final class ListeningSession {
    static final long MAX_DURATION_MS=30000;
    static final long CONTINUATION_MS=2500;
    private boolean active,stopping;
    private boolean awaitingResult;
    private long startedAt,readyAt=-1,attemptAt,waitMs;
    private long lastSpeechAt;
    private String completed="",partial="";
    private int generation,transientRetries;
    boolean begin(long now,boolean followUp){
        if(active)return false;
        active=true;stopping=false;startedAt=now;readyAt=-1;
        waitMs=followUp?10000:12000;transientRetries=0;
        completed="";partial="";awaitingResult=false;lastSpeechAt=now;return true;
    }
    boolean active(){return active;}
    int attempt(long now){attemptAt=now;return ++generation;}
    boolean current(int id){return active&&generation==id;}
    void ready(long now){if(readyAt<0)readyAt=now;}
    void speech(long now){awaitingResult=true;lastSpeechAt=now;}
    void partial(long now,String text){partial=text.trim();if(!partial.isEmpty())speech(now);}
    void result(long now,String text){completed=join(completed,text.trim());partial="";awaitingResult=false;lastSpeechAt=now;}
    void emptyResult(){partial="";awaitingResult=false;}
    String text(){return join(completed,partial);}
    boolean hasResult(){return !completed.isEmpty();}
    boolean awaitingResult(){return awaitingResult;}
    boolean stopping(){return stopping;}
    long pauseRemaining(long now){return awaitingResult?CONTINUATION_MS:Math.max(0,CONTINUATION_MS-(now-lastSpeechAt));}
    boolean submit(long now){return active&&hasResult()&&!awaitingResult&&(stopping||pauseRemaining(now)==0);}
    private static String join(String a,String b){return a.isEmpty()?b:b.isEmpty()?a:a+" "+b;}
    boolean retry(long now,boolean empty,boolean transientFailure){
        if(!active||stopping||now-startedAt>=MAX_DURATION_MS)return false;
        if(empty)return hasResult()?pauseRemaining(now)>0:now-(readyAt<0?startedAt:readyAt)<waitMs;
        if(transientFailure&&now-attemptAt<2500&&transientRetries<2){transientRetries++;return true;}
        return false;
    }
    void stop(){stopping=true;}
    void close(){active=false;++generation;}
}
