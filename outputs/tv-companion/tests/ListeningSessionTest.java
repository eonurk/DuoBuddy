package family.tv.companion;

public final class ListeningSessionTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        ListeningSession s=new ListeningSession();
        check(s.begin(0,false),"first request starts");
        int original=s.attempt(0);s.ready(100);
        check(!s.begin(300,false),"duplicate triggers do not replace or stop listening");
        check(s.current(original),"duplicate preserves active callbacks");
        check(s.retry(600,true,false),"early empty result retries");
        int next=s.attempt(1000);s.ready(1100);
        check(!s.current(original)&&s.current(next),"callbacks from an earlier attempt are ignored");
        check(s.retry(10099,true,false),"initial wait is measured from first readiness");
        check(!s.retry(10100,true,false),"retries cannot extend the ten-second window");
        s.close();check(!s.current(next),"late callbacks after a result are ignored");
        check(s.begin(20000,true),"follow-up starts");s.attempt(20000);s.ready(21000);
        check(s.retry(26999,true,false),"follow-up gives six seconds");
        check(!s.retry(27000,true,false),"follow-up silence ends quietly after its window");
        s.close();s.begin(30000,false);s.attempt(30000);s.ready(30000);s.stop();
        check(!s.retry(30100,true,false),"explicit finish does not restart listening");
        check(!s.retry(30100,false,true),"explicit finish does not retry transient failures");
        s.close();s.begin(40000,false);s.attempt(40000);
        check(s.retry(40100,false,true),"startup microphone conflict retries");s.attempt(40500);
        check(s.retry(40600,false,true),"a second startup conflict retries");s.attempt(41000);
        check(!s.retry(41100,false,true),"persistent microphone failure is bounded");
        check(!s.retry(41100,false,false),"permission and network failures do not loop");
        s.close();s.begin(50000,false);s.attempt(50000);
        check(!s.retry(53000,false,true),"an established session's failure is reported");
        s.ready(79000);check(!s.retry(80000,true,false),"absolute session limit also bounds late readiness");
        s.close();check(!s.retry(80000,true,false),"a closed session never restarts");
        System.out.println("Listening session regression checks passed.");
    }
}
