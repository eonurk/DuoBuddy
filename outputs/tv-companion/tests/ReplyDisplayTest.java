package family.tv.companion;

public final class ReplyDisplayTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        ReplyDisplay d=new ReplyDisplay();
        check(!d.visible("saved-old-reply",1000,false),"opening Buddy must not restore the controller's previous conversation");
        d.received("new",2000);
        check(d.visible("new",2000,false),"fresh answers appear immediately");
        check(d.visible("new",13999,false),"silent replies leave time to read");
        check(!d.visible("new",14000,false),"polling the same reply cannot keep it forever");
        check(d.visible("new",30000,true),"long spoken replies remain visible during speech");
        d.finished(30000);
        check(d.visible("new",37999,false),"answers remain briefly after speech finishes");
        check(!d.visible("new",38000,false),"the welcome screen returns after the reading window");
        check(!d.visible("different",31000,true),"another turn cannot display the previous answer");
        d.clear();
        check(!d.visible("new",31000,true),"starting a request clears the old answer immediately");
        d.finished(32000);
        check(!d.visible("new",33000,false),"a late completion cannot restore a cleared answer");
        System.out.println("Reply display regression checks passed.");
    }
}
