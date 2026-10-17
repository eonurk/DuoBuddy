package family.tv.companion;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

/**
 * Buddy's cosy screensaver: a soft sky for the time of day (stars, a moon and fireflies at
 * night, warm sunset tones in the evening, peach and blue by day) with Buddy on a little
 * hill in a warm glow. Instead of a dashboard, Buddy's speech bubble slowly cycles through
 * kind words, the weather, the word of the day, timers and notes. Buddy is sleepy at night
 * and stretches in the morning, and everything drifts a little against burn-in.
 */
public final class AmbientView extends FrameLayout {
    private static final int CREAM=Color.rgb(255,244,230),INK=Duo.INK;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Sky sky;private final LinearLayout corner;private final TextView clock,date,weatherLine,bubble;private final FrameLayout bubbleBox;private final View bubbleTail;
    private final FaceView face;private Duo.Icon weatherIcon;private final Random random=new Random();
    private JSONObject data=new JSONObject();private long fetchedAt=0;private boolean running=false,stretched=false;
    private int line=0;private String lastBubble="";

    // Kind things Buddy says between the useful ones.
    private static final String[] NIGHT={"Rest easy. I’ll keep watch.","You did lots today. Time to be cosy.","Sweet dreams when you get there.","Slow breaths. Everything can wait till morning.","The stars are out tonight."};
    private static final String[] DAY={"I’m here whenever you need me.","Time for a little break? Maybe some tea.","You’re doing great.","A good day for a good book.","A glass of water might be nice."};
    private static final String[] EVENING={"What a lovely evening.","Blanket, tea, and a good story?","Well done today. Time to unwind.","Time to get comfortable."};

    public AmbientView(Context c){
        super(c);
        sky=new Sky(c);addView(sky,new LayoutParams(-1,-1));
        // a soft warm glow behind Buddy, like a night light
        face=new FaceView(c,true);LayoutParams fp=new LayoutParams(dp(300),dp(300),Gravity.BOTTOM|Gravity.RIGHT);fp.rightMargin=dp(70);fp.bottomMargin=dp(18);addView(face,fp);

        bubbleBox=new FrameLayout(c);
        bubble=new TextView(c);bubble.setTextSize(19);bubble.setTypeface(Fonts.get(c,600));bubble.setLineSpacing(dp(4),1);bubble.setTextColor(INK);bubble.setLineSpacing(dp(3),1);bubble.setMaxWidth(dp(380));bubble.setPadding(dp(24),dp(16),dp(24),dp(18));
        GradientDrawable b=new GradientDrawable();b.setColor(0xf7ffffff);b.setCornerRadius(dp(22));bubble.setBackground(b);bubble.setElevation(dp(6));
        bubbleBox.addView(bubble,new FrameLayout.LayoutParams(-2,-2));
        View tail=new View(c){protected void onDraw(Canvas cv){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Duo.palette(getContext()).SURFACE);Path t=new Path();t.moveTo(0,0);t.lineTo(getWidth(),0);t.lineTo(getWidth()*.85f,getHeight());t.close();cv.drawPath(t,p);}};
        bubbleTail=tail;FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(dp(20),dp(14),Gravity.BOTTOM|Gravity.RIGHT);tp.rightMargin=dp(46);bubbleBox.addView(tail,tp);
        ((FrameLayout.LayoutParams)bubble.getLayoutParams()).bottomMargin=dp(13);
        LayoutParams bp=new LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);bp.rightMargin=dp(250);bp.bottomMargin=dp(250);addView(bubbleBox,bp);

        corner=new LinearLayout(c);corner.setOrientation(LinearLayout.VERTICAL);corner.setClipChildren(false);
        clock=new TextView(c);clock.setTextSize(84);clock.setTypeface(Fonts.get(c,300));clock.setIncludeFontPadding(false);clock.setSingleLine(true);clock.setShadowLayer(dp(14),0,0,0x55000000);
        date=new TextView(c);date.setTextSize(20);date.setSingleLine(true);date.setTypeface(Fonts.get(c,500));
        weatherLine=new TextView(c);weatherLine.setTextSize(17);weatherLine.setTypeface(Fonts.get(c,500));
        corner.addView(clock);corner.addView(date);
        LinearLayout wrow=new LinearLayout(c);wrow.setGravity(Gravity.CENTER_VERTICAL);weatherIcon=new Duo.Icon(c,"cloud",CREAM);wrow.addView(weatherIcon,new LinearLayout.LayoutParams(dp(22),dp(22)));LinearLayout.LayoutParams wl=new LinearLayout.LayoutParams(-2,-2);wl.leftMargin=dp(10);wrow.addView(weatherLine,wl);
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-2,-2);wp.topMargin=dp(14);corner.addView(wrow,wp);
        // Full width, so the clock and date never get clipped to the weather line's width.
        LayoutParams cp=new LayoutParams(-1,-2,Gravity.TOP|Gravity.LEFT);cp.leftMargin=dp(64);cp.topMargin=dp(52);addView(corner,cp);
        render();
    }
    private int dp(float v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}

    public void start(){if(running)return;running=true;stretched=false;handler.post(tick);handler.post(fetch);handler.postDelayed(talk,1500);handler.postDelayed(drift,60000);}
    public void stop(){running=false;handler.removeCallbacksAndMessages(null);}

    private final Runnable tick=new Runnable(){public void run(){if(!running)return;render();handler.postDelayed(this,1000-System.currentTimeMillis()%1000);}};
    private final Runnable fetch=new Runnable(){public void run(){
        if(!running)return;
        Api.request(getContext(),"/api/ambient",null,(d,e)->{if(d!=null){data=d;fetchedAt=SystemClock.uptimeMillis();}render();});
        handler.postDelayed(this,60*1000);
    }};
    /** Buddy says the next thing every few seconds, with a gentle fade. */
    private final Runnable talk=new Runnable(){public void run(){
        if(!running)return;
        ArrayList<String> lines=lines();String next=lines.get(line++%lines.size());
        if(next.equals(lastBubble)&&lines.size()>1)next=lines.get(line++%lines.size());
        final String text=next;lastBubble=text;
        bubbleBox.animate().alpha(0).translationY(dp(4)).setDuration(1600).withEndAction(()->{bubble.setText(text);bubbleBox.animate().alpha(1).translationY(0).setDuration(2200).start();}).start();
        handler.postDelayed(this,Math.max(25000,12000+text.length()*150L));
    }};
    private final Runnable drift=new Runnable(){public void run(){if(!running)return;
        corner.animate().translationX(dp(random.nextInt(17)-8)).translationY(dp(random.nextInt(13)-6)).setDuration(12000).start();
        face.animate().translationX(dp(random.nextInt(11)-5)).setDuration(12000).start();handler.postDelayed(this,60000);}};

    private static int hour(){return Calendar.getInstance().get(Calendar.HOUR_OF_DAY);}
    private static boolean night(int h){return h>=22||h<6;}
    private static String weatherWords(JSONObject w){
        String icon=w.optString("icon"),text=w.optString("text").toLowerCase(Locale.ROOT);int t=w.optInt("temp");
        String feel=icon.equals("rain")||icon.equals("storm")?"Perfect weather for staying in, snug and dry.":icon.equals("snow")?"Hot chocolate weather.":t<=8?"Chilly out there. Wrap up warm.":t>=24?"Warm and lovely out there.":icon.equals("sun")?"Sunny and bright.":"A gentle sort of day.";
        return "It’s "+t+"° and "+text+" outside. "+feel;
    }
    private ArrayList<String> lines(){
        int h=hour();ArrayList<String> out=new ArrayList<>();
        String person=data.optString("person","");JSONArray bday=data.optJSONArray("birthday");
        if(bday!=null&&bday.length()>0)out.add("Happy birthday, "+bday.optString(0)+".");
        String hello=h<5?"Still up? Hello":h<12?"Good morning":h<18?"Good afternoon":h<22?"Good evening":"Hello, night owl";
        out.add(hello.equals("Still up? Hello")?"Still up"+(person.isEmpty()?"":", "+person)+"? Take it easy.":hello+(person.isEmpty()?"":", "+person)+".");
        String[] kind=night(h)?NIGHT:h>=17?EVENING:DAY;
        JSONObject w=data.optJSONObject("weather");if(w!=null)out.add(weatherWords(w));
        out.add(kind[(int)((System.currentTimeMillis()/600000)%kind.length)]);
        JSONObject wd=data.optJSONObject("word");
        if(wd!=null)out.add(wd.optString("language")+" word of the day: “"+wd.optString("word")+"”, which means "+wd.optString("meaning")+".");
        JSONArray timers=data.optJSONArray("timers");long gone=(SystemClock.uptimeMillis()-fetchedAt)/1000;
        if(timers!=null)for(int i=0;i<timers.length();i++){JSONObject t=timers.optJSONObject(i);long left=Math.max(0,t.optLong("secondsLeft")-gone);
            out.add("I’m keeping an eye on "+(t.optString("label").isEmpty()?"your timer":"the "+t.optString("label")+" timer")+": "+Math.max(1,(left+59)/60)+" min to go.");}
        JSONArray notes=data.optJSONArray("notes");
        if(notes!=null)for(int i=0;i<Math.min(2,notes.length());i++)out.add("I remembered: "+notes.optString(i));
        out.add(kind[(int)((System.currentTimeMillis()/600000+2)%kind.length)]);
        return out;
    }
    private void render(){
        Duo.Palette colors=Duo.palette(getContext());bubble.setTextColor(colors.INK);
        ((GradientDrawable)bubble.getBackground()).setColor(colors.SURFACE);bubbleTail.invalidate();
        Calendar now=Calendar.getInstance();int h=now.get(Calendar.HOUR_OF_DAY);
        clock.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(now.getTime()));
        date.setText(new SimpleDateFormat("EEEE d MMMM",Locale.getDefault()).format(now.getTime()));
        JSONObject w=data.optJSONObject("weather");
        weatherLine.setText(w==null?"":w.optInt("temp")+"°  "+w.optString("place").replaceAll(",.*",""));weatherIcon.setVisibility(w==null?View.INVISIBLE:View.VISIBLE);
        if(w!=null){String ic=w.optString("icon");weatherIcon.name(ic.equals("sun")?(w.optBoolean("isDay",true)?"sun":"moon"):ic.equals("rain")||ic.equals("storm")?"rain":ic.equals("snow")?"snow":"cloud");}
        boolean light=sky.light(h);int main=light?Color.rgb(92,58,41):CREAM,muted=light?Color.argb(190,92,58,41):Color.argb(185,255,236,214);
        clock.setTextColor(main);date.setTextColor(muted);weatherLine.setTextColor(muted);if(weatherIcon!=null)weatherIcon.color(muted);clock.setShadowLayer(dp(14),0,0,light?0x22ffffff:0x55000000);
        if(night(h))face.setMood("sleepy");
        else if(h>=6&&h<10&&!stretched){stretched=true;face.setMood("stretch");}
        else if(!face.mood().equals("stretch"))face.setMood(h>=17?"aww":"");
    }
    private static String icon(String name,boolean day){
        switch(name){case "sun":return day?"☀️":"🌙";case "partly":return day?"⛅":"☁️";case "rain":return "🌧️";case "snow":return "❄️";case "storm":return "⛈️";case "fog":return "🌫️";default:return "☁️";}
    }

    /** The painted backdrop: sky gradient, stars and moon or sun, clouds, fireflies and a hill. */
    private static final class Sky extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final float[] stars=new float[3*46],flies=new float[4*6],clouds=new float[3*4];
        Sky(Context c){super(c);Random r=new Random(7);
            for(int i=0;i<stars.length;i+=3){stars[i]=r.nextFloat();stars[i+1]=r.nextFloat()*.62f;stars[i+2]=r.nextFloat()*6.28f;}
            for(int i=0;i<flies.length;i+=4){flies[i]=r.nextFloat();flies[i+1]=.55f+r.nextFloat()*.35f;flies[i+2]=r.nextFloat()*6.28f;flies[i+3]=.6f+r.nextFloat();}
            for(int i=0;i<clouds.length;i+=3){clouds[i]=r.nextFloat();clouds[i+1]=.1f+r.nextFloat()*.35f;clouds[i+2]=.7f+r.nextFloat()*.6f;}}
        boolean light(int h){return h>=7&&h<17;}
        private int[] palette(int h){
            // top, bottom, hill, glow
            if(h>=22||h<5)return new int[]{0xff141233,0xff3a2c5e,0xff221c40,0x66ffcf8a};
            if(h<7)return new int[]{0xff2d2a5a,0xffe8a98a,0xff4a3a5e,0x66ffd9a0};
            if(h<10)return new int[]{0xffffd9b8,0xfffff2e2,0xffcfe3b0,0x55ffffff};
            if(h<17)return new int[]{0xffa9d8f5,0xfffff3e3,0xffbfe0a8,0x55ffffff};
            return new int[]{0xff3b2f6e,0xfff0a17e,0xff4e3a66,0x77ffcf8a};
        }
        protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),t=SystemClock.uptimeMillis()/1000f;int hr=hour();int[] pal=palette(hr);boolean dark=!light(hr);
            p.setColor(0xff000000);p.setShader(new LinearGradient(0,0,0,h,pal[0],pal[1],Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
            if(dark){for(int i=0;i<stars.length;i+=3){float tw=.35f+.65f*(.5f+.5f*(float)Math.sin(t*.6f+stars[i+2]));p.setColor(Color.argb((int)(220*tw),255,246,220));c.drawCircle(stars[i]*w,stars[i+1]*h,(i%9==0?1.9f:1.1f)*getResources().getDisplayMetrics().density,p);}}
            // moon at night, a soft sun by day, both with a halo
            float mx=w*.56f,my=h*.2f,mr=h*.06f;
            p.setColor(0xff000000);p.setShader(new RadialGradient(mx,my,mr*4,dark?0x44fff1c8:0x66fff6d0,0x00ffffff,Shader.TileMode.CLAMP));c.drawCircle(mx,my,mr*4,p);p.setShader(null);
            if(dark&&(hr>=21||hr<6)){Path moon=new Path();moon.addCircle(mx,my,mr,Path.Direction.CW);Path bite=new Path();bite.addCircle(mx+mr*.45f,my-mr*.2f,mr*.85f,Path.Direction.CW);moon.op(bite,Path.Op.DIFFERENCE);p.setColor(0xfffff1c8);c.drawPath(moon,p);}
            else{p.setColor(dark?0xffffc58a:0xfffff0b8);c.drawCircle(mx,my,mr*.9f,p);}
            // slow clouds
            for(int i=0;i<clouds.length;i+=3){float s=clouds[i+2]*h*.16f,x=((clouds[i]+t*.0015f*clouds[i+2])%1.4f-.2f)*w,y=clouds[i+1]*h;int a=dark?0x16:0x5a;
                p.setColor(0xff000000);p.setShader(new RadialGradient(x,y,s*2,new int[]{(a<<24)|0xffffff,0x00ffffff},null,Shader.TileMode.CLAMP));c.save();c.scale(1,.38f,x,y);c.drawCircle(x,y,s*2,p);c.restore();p.setShader(null);}
            // the hill Buddy sits on
            Path hill=new Path();hill.moveTo(0,h*.86f);hill.cubicTo(w*.3f,h*.78f,w*.55f,h*.9f,w*.72f,h*.8f);hill.cubicTo(w*.84f,h*.74f,w*.95f,h*.78f,w,h*.8f);hill.lineTo(w,h);hill.lineTo(0,h);hill.close();
            float gx=w*.86f,gy=h*.74f;p.setColor(0xff000000);p.setShader(new RadialGradient(gx,gy,h*.42f,pal[3],0x00ffffff,Shader.TileMode.CLAMP));c.drawCircle(gx,gy,h*.42f,p);p.setShader(null);
            p.setColor(pal[2]);c.drawPath(hill,p);
            // fireflies drifting up in the dark
            if(dark)for(int i=0;i<flies.length;i+=4){float f=((t*.008f*flies[i+3])+flies[i])%1,x=(flies[i]+.03f*(float)Math.sin(t*.3f+flies[i+2]))*w,y=(flies[i+1]-f*.35f)*h;float glow=.5f+.5f*(float)Math.sin(t*.9f+flies[i+2]);
                p.setColor(0xff000000);p.setShader(new RadialGradient(x,y,10,Color.argb((int)(160*glow),255,214,120),0x00ffd678,Shader.TileMode.CLAMP));c.drawCircle(x,y,10,p);p.setShader(null);p.setColor(Color.argb((int)(220*glow),255,236,170));c.drawCircle(x,y,1.8f,p);}
            postInvalidateDelayed(50);
        }
    }
}
