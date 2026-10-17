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
        check(s.retry(12099,true,false),"initial wait is measured from first readiness");
        check(!s.retry(12100,true,false),"retries cannot extend the twelve-second window");
        s.close();check(!s.current(next),"late callbacks after a result are ignored");
        check(s.begin(20000,true),"follow-up starts");s.attempt(20000);s.ready(21000);
        check(s.retry(30999,true,false),"follow-up gives ten seconds");
        check(!s.retry(31000,true,false),"follow-up silence ends quietly after its window");
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

        s.begin(90000,false);int firstSegment=s.attempt(90000);s.ready(90100);
        s.speech(90500);s.partial(90600,"can you");s.result(91000,"Can you");
        check(!s.submit(91000),"a final engine result does not submit immediately");
        check(!s.submit(93499),"a natural pause is allowed after a short result");
        check(s.retry(92000,true,false),"empty continuation attempts do not discard the first segment");
        s.attempt(91200);check(!s.current(firstSegment),"duplicate results from a completed attempt are ignored");
        s.speech(92500);s.partial(93000,"tell me");
        check(!s.submit(95000),"continuing speech blocks submission even past the old pause deadline");
        check(s.text().equals("Can you tell me"),"the live caption includes completed and partial speech");
        s.result(96000,"tell me the time");
        check(s.text().equals("Can you tell me the time"),"recognized segments form one request");
        check(!s.submit(98499)&&s.submit(98500),"one request is ready after the continuation pause");
        s.close();check(!s.submit(99000),"a delayed submit cannot send a closed session");

        s.begin(100000,false);s.attempt(100000);s.result(101000,"No");s.result(102000,"no");
        check(s.text().equals("No no"),"intentional repeated words are retained");
        s.stop();check(s.submit(102000),"explicit finish bypasses the continuation pause");
        check(!s.retry(102000,true,false),"explicit finish never reopens the microphone");
        s.close();s.begin(110000,false);s.attempt(110000);
        check(s.text().isEmpty(),"a new session cannot inherit the previous request");
        s.partial(111000,"Set a timer");s.stop();
        check(s.text().equals("Set a timer"),"explicit finish can preserve the latest partial if the engine supplies no final result");
        s.close();s.begin(120000,false);s.attempt(120000);s.result(149000,"Still speaking");
        check(!s.retry(150000,true,false),"continuation never extends the absolute microphone limit");
        System.out.println("Listening session regression checks passed.");
    }
}
