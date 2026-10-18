package family.tv.companion;

import android.accessibilityservice.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import org.json.*;
import java.util.concurrent.*;

/** Optional user-enabled Android navigation. Never uses ADB, shell or hidden input APIs. */
public final class TvAccess extends AccessibilityService {
    private static volatile TvAccess current;
    private final Handler main=new Handler(Looper.getMainLooper());
    protected void onServiceConnected(){current=this;}
    private static final String GOOGLE_ASSISTANT="com.google.android.katniss";
    private long lastTakeover;
    /** Some firmware (Mi Box) sends the remote's mic key straight to Google's assistant before apps or key filters see it, so replace its voice window with Buddy.
     *  Google keeps that window as a reusable overlay, so showing it does not always raise a window-state event. */
    public void onAccessibilityEvent(AccessibilityEvent event){
        int type=event.getEventType();
        if(type!=AccessibilityEvent.TYPE_WINDOWS_CHANGED&&!GOOGLE_ASSISTANT.equals(String.valueOf(event.getPackageName())))return;
        // Focus reaches Google's window shortly after its events, so check again for a moment.
        String reason=AccessibilityEvent.eventTypeToString(type);
        for(long delay:new long[]{0,150,400,800})main.postDelayed(()->takeOverGoogle(reason),delay);
    }
    private void takeOverGoogle(String reason){
        long now=SystemClock.uptimeMillis();if(now-lastTakeover<1500)return;
        // Back only when Google's window has focus, so it can never leave the app being watched.
        if(!googleWindowActive())return;
        lastTakeover=now;
        android.util.Log.i("BuddyVoice","Remote assistant button taken over from Google ("+reason+")");
        performGlobalAction(GLOBAL_ACTION_BACK);
        String action=CompanionService.listening?CompanionService.FINISH_LISTEN:CompanionService.LISTEN;
        main.postDelayed(()->startForegroundService(new Intent(this,CompanionService.class).setAction(action)),800);
    }
    private boolean googleWindowActive(){
        AccessibilityNodeInfo active=getRootInActiveWindow();
        if(active!=null)try{if(GOOGLE_ASSISTANT.equals(String.valueOf(active.getPackageName())))return true;}finally{active.recycle();}
        for(AccessibilityWindowInfo window:getWindows()){
            if(!window.isActive()&&!window.isFocused())continue;
            AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;
            try{if(GOOGLE_ASSISTANT.equals(String.valueOf(root.getPackageName())))return true;}finally{root.recycle();}
        }
        return false;
    }
    public void onInterrupt(){}
    public void onDestroy(){if(current==this)current=null;super.onDestroy();}
    protected boolean onKeyEvent(KeyEvent event){
        int key=event.getKeyCode();
        if(key==KeyEvent.KEYCODE_ASSIST||key==KeyEvent.KEYCODE_VOICE_ASSIST||key==KeyEvent.KEYCODE_SEARCH){
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)startForegroundService(new Intent(this,CompanionService.class).setAction(CompanionService.LISTEN));return true;
        }
        return false;
    }
    static boolean enabled(){return current!=null;}
    static JSONObject call(String action,JSONObject args)throws Exception{
        TvAccess service=current;if(service==null)throw new IllegalStateException("Enable TV control in DuoBuddy Settings → Accessibility first.");
        FutureTask<JSONObject> task=new FutureTask<>(()->service.perform(action,args));service.main.post(task);return task.get(5,TimeUnit.SECONDS);
    }
    static String foreground(){
        TvAccess service=current;if(service==null)return "Unknown (TV control is not enabled)";
        AccessibilityNodeInfo root=service.getRootInActiveWindow();if(root==null)return "Unknown";try{return String.valueOf(root.getPackageName());}finally{root.recycle();}
    }
    private JSONObject perform(String action,JSONObject args)throws Exception{
        if(action.equals("remote")){
            String key=args.optString("key");int times=args.isNull("times")?1:args.optInt("times",1);if(times<1||times>25)throw new IllegalArgumentException("Repeat count must be 1–25.");
            if(key.equals("back")||key.equals("home")||key.equals("sleep")){
                int global=key.equals("back")?GLOBAL_ACTION_BACK:key.equals("home")?GLOBAL_ACTION_HOME:GLOBAL_ACTION_LOCK_SCREEN;
                if(!performGlobalAction(global))throw new IllegalStateException("Android did not accept "+key+".");return TvJson.object("message","Sent "+key+".");
            }
            AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)throw new IllegalStateException("No accessible screen is available.");
            try{for(int n=0;n<times;n++){
                AccessibilityNodeInfo focused=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);if(focused==null)focused=root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);if(focused==null)focused=AccessibilityNodeInfo.obtain(root);
                try{
                    if(key.equals("select")||key.equals("enter")){
                        if(!focused.performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new IllegalStateException("The focused item does not expose a click action.");
                    }else if(key.equals("page_down")||key.equals("page_up")){
                        if(!scroll(root,key.equals("page_down")))throw new IllegalStateException("This screen does not expose scrolling.");
                    }else{
                        int direction=key.equals("up")?View.FOCUS_UP:key.equals("down")?View.FOCUS_DOWN:key.equals("left")?View.FOCUS_LEFT:key.equals("right")?View.FOCUS_RIGHT:0;
                        if(direction==0)throw new IllegalArgumentException("That remote key is unavailable without system privileges.");
                        AccessibilityNodeInfo next=focused.focusSearch(direction);if(next==null)throw new IllegalStateException("No accessible item in that direction.");
                        try{if(!next.performAction(AccessibilityNodeInfo.ACTION_FOCUS)&&!next.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))throw new IllegalStateException("Could not move focus.");}finally{next.recycle();}
                    }
                }finally{focused.recycle();}
            }}finally{root.recycle();}
            return TvJson.object("message","Sent "+key+". Check screen text to verify the result.");
        }
        if(action.equals("tap")){
            android.util.DisplayMetrics size=getResources().getDisplayMetrics();int x=args.optInt("x",-1),y=args.optInt("y",-1);
            if(x<0||y<0||x>=size.widthPixels||y>=size.heightPixels)throw new IllegalArgumentException("Tap is outside the screen.");
            Path path=new Path();path.moveTo(x,y);GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,80)).build();
            if(!dispatchGesture(gesture,null,null))throw new IllegalStateException("Android did not accept the tap.");return TvJson.object("message","Tap dispatched. Read screen text to verify.");
        }
        AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)throw new IllegalStateException("This app does not expose accessible screen content.");
        try{
            if(action.equals("type_text")){
                AccessibilityNodeInfo edit=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);if(edit==null)throw new IllegalStateException("Focus a text field first.");
                try{
                    if(edit.isPassword())throw new IllegalStateException("DuoBuddy does not type passwords or sign-in credentials.");
                    Bundle bundle=new Bundle();bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,LocalData.printable(args.optString("text"),200));
                    if(!edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,bundle))throw new IllegalStateException("This field does not support accessible text entry.");
                    return TvJson.object("message",args.optBoolean("submit")?"Text entered. Enter/submit is not exposed here; use an accessible Search button to submit.":"Text entered.");
                }finally{edit.recycle();}
            }
            JSONArray nodes=new JSONArray();read(root,nodes,0);
            return TvJson.object("message","Accessible screen text only; this is not a screenshot. Hidden and protected video content is unavailable.","package",String.valueOf(root.getPackageName()),"nodes",nodes);
        }finally{root.recycle();}
    }
    private static boolean scroll(AccessibilityNodeInfo node,boolean forward){
        if(node.isScrollable()&&node.performAction(forward?AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))return true;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child==null)continue;try{if(scroll(child,forward))return true;}finally{child.recycle();}}return false;
    }
    private static void read(AccessibilityNodeInfo node,JSONArray out,int depth){
        if(depth>15||out.length()>=150||node.isPassword()||!node.isVisibleToUser())return;
        String text=node.getText()==null?"":node.getText().toString(),description=node.getContentDescription()==null?"":node.getContentDescription().toString();
        if(!text.isEmpty()||!description.isEmpty()){
            Rect bounds=new Rect();node.getBoundsInScreen(bounds);
            out.put(TvJson.object("text",text.substring(0,Math.min(250,text.length())),"description",description.substring(0,Math.min(250,description.length())),"clickable",node.isClickable(),"focused",node.isFocused(),"x",bounds.centerX(),"y",bounds.centerY()));
        }
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child==null)continue;try{read(child,out,depth+1);}finally{child.recycle();}}
    }
}
