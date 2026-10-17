package family.tv.companion;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Buddy's TV design system, in DuoBook's brand: Quicksand, slate text, one orange accent.
 * Buddy is the only character on screen; everything around him stays quiet. Orange is
 * reserved for the Talk action and for remote-control focus, which also lifts a control
 * onto a white surface. Icons are drawn as 1.8-unit line icons, never emoji.
 */
final class Duo {
    static final int CANVAS=0xfff6f7f9,WHITE=0xffffffff,INK=0xff1e293b,BODY=0xff334155,SECOND=0xff64748b,MUTED=0xff94a3b8,LINE=0xffe2e8f0,
        ORANGE=0xfff97316,ORANGE_DARK=0xffea580c,ORANGE_TINT=0xfffff1e6,GREEN=0xff22a06b,SKY=0xff0ea5e9;

    static int dp(Context c,float v){return(int)(v*c.getResources().getDisplayMetrics().density+.5f);}
    static GradientDrawable round(Context c,int fill,float radius,int stroke,float strokeWidth){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(c,radius));if(strokeWidth>0)d.setStroke(dp(c,strokeWidth),stroke);return d;
    }
    static TextView text(Context c,String value,float size,int color,int weight){
        TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Fonts.get(c,weight));t.setIncludeFontPadding(false);return t;
    }

    /** A line icon on a 24-unit grid. */
    static final class Icon extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();private String name;
        Icon(Context c,String name,int color){super(c);this.name=name;p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(color);}
        void color(int c){p.setColor(c);invalidate();}
        void name(String n){name=n;invalidate();}
        protected void onDraw(Canvas c){
            float s=Math.min(getWidth(),getHeight())/24f;c.save();c.translate((getWidth()-24*s)/2,(getHeight()-24*s)/2);c.scale(s,s);p.setStrokeWidth(1.8f);path.reset();
            switch(name){
                case "play":c.drawCircle(12,12,9,p);path.moveTo(10.2f,8.6f);path.lineTo(15.4f,12);path.lineTo(10.2f,15.4f);path.close();c.drawPath(path,p);break;
                case "music":path.moveTo(9,17.5f);path.lineTo(9,5.5f);path.lineTo(19,3.8f);path.lineTo(19,15.5f);c.drawPath(path,p);c.drawCircle(6.6f,17.5f,2.4f,p);c.drawCircle(16.6f,15.5f,2.4f,p);break;
                case "timer":c.drawCircle(12,13.5f,7.5f,p);c.drawLine(12,13.5f,12,9.5f,p);c.drawLine(9.5f,3,14.5f,3,p);c.drawLine(18.2f,7.2f,19.6f,5.8f,p);break;
                case "moon":path.moveTo(19.5f,14.5f);path.cubicTo(15,16.5f,8.5f,13.5f,9.5f,4.5f);path.cubicTo(5.5f,6,3.5f,10,4.5f,14);path.cubicTo(5.8f,19,12,21.5f,16.5f,19);path.cubicTo(17.8f,18.2f,18.9f,16.5f,19.5f,14.5f);c.drawPath(path,p);break;
                case "mic":c.drawRoundRect(9,3,15,14,3,3,p);path.moveTo(5.5f,11);path.cubicTo(5.5f,19,18.5f,19,18.5f,11);c.drawPath(path,p);c.drawLine(12,17.5f,12,21,p);break;
                case "apps":for(int i=0;i<2;i++)for(int j=0;j<2;j++)c.drawRoundRect(4+i*9,4+j*9,11+i*9,11+j*9,2,2,p);break;
                case "style":path.moveTo(12,3.5f);path.lineTo(13.9f,9.2f);path.lineTo(19.8f,9.4f);path.lineTo(15.1f,13);path.lineTo(16.8f,18.8f);path.lineTo(12,15.4f);path.lineTo(7.2f,18.8f);path.lineTo(8.9f,13);path.lineTo(4.2f,9.4f);path.lineTo(10.1f,9.2f);path.close();c.drawPath(path,p);break;
                case "keyboard":c.drawRoundRect(2.5f,6,21.5f,18,2.5f,2.5f,p);for(int i=0;i<4;i++){c.drawPoint(6.5f+i*3.7f,10,p);}c.drawLine(8,14.2f,16,14.2f,p);break;
                case "sun":c.drawCircle(12,12,4,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(12+(float)Math.cos(a)*7,12+(float)Math.sin(a)*7,12+(float)Math.cos(a)*9,12+(float)Math.sin(a)*9,p);}break;
                case "rain":cloud(c);for(int i=0;i<3;i++)c.drawLine(8+i*4,18.5f,7+i*4,21,p);break;
                case "snow":cloud(c);for(int i=0;i<3;i++)c.drawPoint(8+i*4,20,p);break;
                default:cloud(c);
            }
            c.restore();
        }
        private void cloud(Canvas c){path.moveTo(7,16.5f);path.cubicTo(3.5f,16.5f,3.2f,11.6f,6.8f,11.2f);path.cubicTo(7.3f,6.5f,14.5f,5.6f,16,10.2f);path.cubicTo(20.2f,10,21,16.5f,17,16.5f);path.close();c.drawPath(path,p);}
    }

    /** A suggestion row: icon and label; focus lifts it onto white with an orange icon. */
    static LinearLayout row(Context c,String icon,String label,Runnable click){
        LinearLayout r=new LinearLayout(c);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(c,16),0,dp(c,16),0);r.setFocusable(true);r.setClickable(true);r.setOnClickListener(v->click.run());
        Icon i=new Icon(c,icon,SECOND);r.addView(i,new LinearLayout.LayoutParams(dp(c,22),dp(c,22)));
        TextView t=text(c,label,17,BODY,600);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=dp(c,16);r.addView(t,tp);
        r.setOnFocusChangeListener((v,on)->{r.setBackground(on?round(c,WHITE,14,0,0):null);r.setElevation(on?dp(c,6):0);i.color(on?ORANGE:SECOND);t.setTextColor(on?INK:BODY);});
        return r;
    }
    /** The one primary action: an orange pill with an icon. */
    static LinearLayout primary(Context c,String icon,String label,Runnable click){
        LinearLayout b=action(c,icon,label,click);b.setBackground(round(c,ORANGE,999,0,0));((Icon)b.getChildAt(0)).color(WHITE);((TextView)b.getChildAt(1)).setTextColor(WHITE);
        b.setOnFocusChangeListener((v,on)->{b.setBackground(round(c,on?ORANGE_DARK:ORANGE,999,0,0));b.setElevation(on?dp(c,6):0);b.animate().scaleX(on?1.04f:1).scaleY(on?1.04f:1).setDuration(140).start();});
        return b;
    }
    /** A quiet action: icon and label on the canvas, lifted onto white when focused. */
    static LinearLayout quiet(Context c,String icon,String label,Runnable click){
        LinearLayout b=action(c,icon,label,click);Icon i=(Icon)b.getChildAt(0);TextView t=(TextView)b.getChildAt(1);
        b.setOnFocusChangeListener((v,on)->{b.setBackground(on?round(c,WHITE,999,0,0):null);b.setElevation(on?dp(c,6):0);i.color(on?ORANGE:SECOND);t.setTextColor(on?INK:BODY);});
        return b;
    }
    private static LinearLayout action(Context c,String icon,String label,Runnable click){
        LinearLayout b=new LinearLayout(c);b.setGravity(Gravity.CENTER);b.setPadding(dp(c,18),0,dp(c,20),0);b.setFocusable(true);b.setClickable(true);b.setOnClickListener(v->click.run());
        b.addView(new Icon(c,icon,SECOND),new LinearLayout.LayoutParams(dp(c,20),dp(c,20)));
        TextView t=text(c,label,15,BODY,700);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.leftMargin=dp(c,9);b.addView(t,tp);
        return b;
    }

    /** A soft tinted disc and floor shadow that Buddy stands on. */
    static final class Stage extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Stage(Context c){super(c);}
        protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),r=Math.min(w,h)*.42f,cx=w/2,cy=h*.5f;
            p.setColor(0xff000000);p.setShader(new RadialGradient(cx,cy,r,new int[]{ORANGE_TINT,0xfffff6ef,0x00fff6ef},new float[]{0,.7f,1},Shader.TileMode.CLAMP));c.drawCircle(cx,cy,r,p);p.setShader(null);
            float fy=cy+r*.72f;p.setShader(new RadialGradient(cx,fy,r*.55f,new int[]{0x1f1e293b,0x001e293b},null,Shader.TileMode.CLAMP));c.save();c.scale(1,.16f,cx,fy);c.drawCircle(cx,fy,r*.55f,p);c.restore();p.setShader(null);
        }
    }
}
