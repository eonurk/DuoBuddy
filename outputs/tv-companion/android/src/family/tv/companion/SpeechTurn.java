package family.tv.companion;

import java.util.concurrent.atomic.AtomicLong;

/** Invalidates native callbacks and queued UI events when speech is replaced or stopped. */
final class SpeechTurn {
    private final AtomicLong serial=new AtomicLong();
    private volatile boolean closed;
    long next(){return serial.incrementAndGet();}
    boolean current(long turn){return !closed&&serial.get()==turn;}
    void close(){closed=true;next();}
}
