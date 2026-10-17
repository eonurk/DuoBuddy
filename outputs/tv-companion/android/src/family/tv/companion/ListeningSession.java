package family.tv.companion;

/** Bounded retries for TV recognizers that finish before the user starts speaking. */
final class ListeningSession {
    static final long MAX_DURATION_MS=30000;
    private boolean active,stopping;
    private long startedAt,readyAt=-1,attemptAt,waitMs;
    private int generation,transientRetries;
    boolean begin(long now,boolean followUp){
        if(active)return false;
        active=true;stopping=false;startedAt=now;readyAt=-1;
        waitMs=followUp?6000:10000;transientRetries=0;return true;
    }
    boolean active(){return active;}
    int attempt(long now){attemptAt=now;return ++generation;}
    boolean current(int id){return active&&generation==id;}
    void ready(long now){if(readyAt<0)readyAt=now;}
    boolean retry(long now,boolean empty,boolean transientFailure){
        if(!active||stopping||now-startedAt>=MAX_DURATION_MS)return false;
        if(empty)return now-(readyAt<0?startedAt:readyAt)<waitMs;
        if(transientFailure&&now-attemptAt<2500&&transientRetries<2){transientRetries++;return true;}
        return false;
    }
    void stop(){stopping=true;}
    void close(){active=false;++generation;}
}
