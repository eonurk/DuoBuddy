package family.tv.companion;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import com.caverock.androidsvg.SVG;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.HashMap;

/**
 * DuoBook's PracticeBuddy, drawn live from PracticeBuddy.jsx (same 120×120 box, shapes and
 * palettes) with the app's App.css motion: the bob, blinks, an idle glance, the listening
 * sway with its floating note, and a happy hop when Buddy starts talking.
 * The wardrobe (hat, eyewear, neck, face) layers in the component's order.
 * Moods add TV-only poses: sleepy and stretch for the screensaver, and quick reactions to
 * what's on (scared under a blanket, cheer, groove, laugh, aww, sad).
 * Outfit slots resolve as: a temporary reaction outfit, then the saved Style choice.
 * Seasonal outfits are suggestions in Style and only become saved choices when accepted.
 */
public final class FaceView extends View {
    private static final int INK=0xff7c2d12,PUPIL=0xff1e293b,WHITE=0xffffffff,TONGUE=0xfffb7185,NOTE=0xff0ea5e9;
    /** One CSS px on the app's 78px-wide buddy, in viewBox units. */
    private static final float PX=120f/78f;
    private static JSONObject palettes;private static JSONArray wardrobe;
    private static final HashMap<String,Picture> outfit=new HashMap<>();
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),line=new Paint(Paint.ANTI_ALIAS_FLAG),text=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path(),cover=new Path();
    private final boolean small;
    public String phase="idle";public boolean talking=false;
    private boolean wasTalking=false;private long hopStart=-10000;
    private String mood="";private long moodStart=0;
    /** Temporary outfit for a reaction, and the team scarf's colours. */
    public JSONObject dressUp=null;public int scarfMain=0xffe11d48,scarfStripe=WHITE;

    public FaceView(Context c,boolean mini){
        super(c);small=mini;setContentDescription(Duo.name(c)+", your TV companion");
        line.setStyle(Paint.Style.STROKE);line.setStrokeCap(Paint.Cap.ROUND);line.setStrokeJoin(Paint.Join.ROUND);
        cover.addRoundRect(new RectF(22,16,98,108),20,20,Path.Direction.CW);
    }

    public void setMood(String m){m=m==null?"":m;if(!m.equals(mood)){mood=m;moodStart=SystemClock.uptimeMillis();}}
    public String mood(){return mood;}
    private String slot(android.content.SharedPreferences prefs,String name){
        if(dressUp!=null&&dressUp.has(name))return dressUp.optString(name);
        return prefs.getString(name,"");
    }
    private JSONObject palette(String variant){
        if(palettes==null)try{palettes=new JSONObject(asset(getContext(),"buddy/palettes.json"));}catch(Exception e){android.util.Log.e("BuddyArt","Cannot load palettes",e);palettes=new JSONObject();}
        JSONObject p=palettes.optJSONObject(variant);if(p==null)p=palettes.optJSONObject("tangerine");return p==null?new JSONObject():p;
    }
    static String asset(Context c,String name)throws Exception{try(InputStream in=c.getAssets().open(name)){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    /** DuoBook's wardrobe in shelf order: {id, slot, name, hidesSprout}. */
    static JSONArray wardrobe(Context c){if(wardrobe==null)try{wardrobe=new JSONArray(asset(c,"buddy/wardrobe.json"));}catch(Exception e){android.util.Log.e("BuddyArt","Cannot load wardrobe",e);wardrobe=new JSONArray();}return wardrobe;}
    private boolean hidesSprout(String id){JSONArray all=wardrobe(getContext());for(int i=0;i<all.length();i++){JSONObject o=all.optJSONObject(i);if(o.optString("id").equals(id))return o.optBoolean("hidesSprout");}return false;}
    private void item(Canvas c,String id){if(id.isEmpty())return;if(id.equals("teamscarf")){teamScarf(c);return;}if(!outfit.containsKey(id)){Picture pic=null;try{pic=SVG.getFromAsset(getContext().getAssets(),"buddy/wardrobe/"+id+".svg").renderToPicture(120,120);}catch(Exception ignored){}outfit.put(id,pic);}Picture pic=outfit.get(id);if(pic!=null)c.drawPicture(pic);}
    private static int color(JSONObject p,String key,String fallback){try{return Color.parseColor(p.optString(key,fallback));}catch(Exception e){return Color.parseColor(fallback);}}
    private static float ease(float x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    /** CSS-style keyframes with ease-in-out between stops. */
    private static float keys(float f,float[] at,float[] v){for(int i=1;i<at.length;i++)if(f<=at[i])return v[i-1]+(v[i]-v[i-1])*ease((f-at[i-1])/(at[i]-at[i-1]));return v[v.length-1];}
    private static float cycle(float t,float period){return(t%period)/period;}

    private void oval(Canvas c,float cx,float cy,float rx,float ry,int color){fill.setColor(color);c.drawOval(cx-rx,cy-ry,cx+rx,cy+ry,fill);}
    private void circle(Canvas c,float cx,float cy,float r,int color){fill.setColor(color);c.drawCircle(cx,cy,r,fill);}
    private void stroke(Canvas c,int color,float width){line.setColor(color);line.setStrokeWidth(width);c.drawPath(path,line);}
    private void label(Canvas c,String s,float x,float y,float size,int color){text.setColor(color);text.setTypeface(Fonts.get(getContext(),600));text.setTextSize(size);text.setTextAlign(Paint.Align.CENTER);c.drawText(s,x,y,text);}

    /** The scarf from DuoBook's wardrobe, knitted in a team's colours. */
    private void teamScarf(Canvas c){
        RectF band=new RectF(20,87,100,97),tail=new RectF(28,95,39,107);
        for(RectF r:new RectF[]{band,tail}){fill.setColor(scarfMain);c.drawRoundRect(r,r==band?5:4,r==band?5:4,fill);line.setColor(0x55000000);line.setStrokeWidth(1.2f);c.drawRoundRect(r,r==band?5:4,r==band?5:4,line);}
        line.setColor(scarfStripe);line.setStrokeWidth(3);for(float x:new float[]{34,50,66,82})c.drawLine(x,89,x-1.5f,95,line);
        c.drawLine(28.8f,100.5f,38.2f,100.5f,line);line.setStrokeWidth(2);for(float x:new float[]{30.5f,33.5f,36.5f})c.drawLine(x,106.5f,x,110.5f,line);
    }
    /** Closed, smiling eyes (^ ^). */
    private void happyEyes(Canvas c){for(float x:new float[]{46,78}){path.reset();path.moveTo(x-9,58);path.quadTo(x,46,x+9,58);stroke(c,PUPIL,4.2f);}}
    private static float hash(int i){float v=(float)Math.sin(i*12.9898)*43758.547f;return v-(float)Math.floor(v);}
    /** How open the mouth is while talking, 0–1: soft syllables of uneven size, with a breath between phrases. */
    private static float talkOpen(float t){
        float k=t*4.6f,frac=k-(float)Math.floor(k),s=(float)Math.sin(Math.PI*frac);
        float phrase=(t%2.3f)/2.3f,breath=1-ease((phrase-.8f)/.08f)+ease((phrase-.94f)/.06f);
        return Math.min(1,breath)*(.35f+.65f*hash((int)Math.floor(k)))*s*s*(3-2*s);
    }
    private void teeth(Canvas c,float y){for(float x:new float[]{56.7f,62.8f}){fill.setColor(WHITE);c.drawRoundRect(x,y,x+4.5f,y+4.8f,1.9f,1.9f,fill);line.setColor(INK);line.setStrokeWidth(1);c.drawRoundRect(x,y,x+4.5f,y+4.8f,1.9f,1.9f,line);}}
    /** Face-slot details from PracticeBuddy.jsx; they sit on top of every mood. */
    private void faceDetail(Canvas c,String face){
        if(face.equals("freckles")){fill.setColor(0xff9a3412);fill.setAlpha(184);float[] f={36,69,1.15f,40.5f,71,1,44,68.5f,.9f,80,68.5f,.9f,83.5f,71,1,88,69,1.15f};for(int i=0;i<f.length;i+=3)c.drawCircle(f[i],f[i+1],f[i+2],fill);fill.setAlpha(255);}
        else if(face.equals("beautymark")){circle(c,87,73,2,0xff713f12);fill.setColor(WHITE);fill.setAlpha(140);c.drawCircle(86.4f,72.4f,.55f,fill);fill.setAlpha(255);}
        else if(face.equals("moustache")){path.reset();path.moveTo(62,76);path.cubicTo(57,70,49,72,49,77);path.cubicTo(52,76,54,78,56,79);path.cubicTo(59,80,61,78,62,76);path.cubicTo(63,78,65,80,68,79);path.cubicTo(70,78,72,76,75,77);path.cubicTo(75,72,67,70,62,76);path.close();fill.setColor(0xff713f12);c.drawPath(path,fill);stroke(c,0xff451a03,.8f);}
        else if(face.equals("starcheek")){float[] v={88,65.5f,89.7f,69,93.5f,69.5f,90.7f,72.2f,91.4f,76,88,74.2f,84.6f,76,85.3f,72.2f,82.5f,69.5f,86.3f,69};path.reset();path.moveTo(v[0],v[1]);for(int i=2;i<v.length;i+=2)path.lineTo(v[i],v[i+1]);path.close();fill.setColor(0xfffde047);c.drawPath(path,fill);stroke(c,0xfff59e0b,1);}
    }

    protected void onDraw(Canvas c){
        super.onDraw(c);float w=getWidth(),h=getHeight();if(w==0||h==0)return;
        long now=SystemClock.uptimeMillis();float t=now/1000f;
        if(talking&&!wasTalking)hopStart=now;wasTalking=talking;
        boolean listening=phase.equals("listening"),reading=!listening&&(phase.equals("thinking")||phase.equals("acting")),speaking=talking&&!listening;
        // Buddy's own phases (listening, talking) win over a mood.
        String m=listening||speaking||reading?"":mood;float mt=(now-moodStart)/1000f;
        if(m.equals("stretch")&&mt>2.6f)m="";
        float talk=speaking?talkOpen(t):0;
        android.content.SharedPreferences prefs=Api.prefs(getContext());JSONObject p=palette(prefs.getString("variant","tangerine"));
        String hat=slot(prefs,"hat"),eyewear=slot(prefs,"eyewear"),neck=slot(prefs,"neck"),face=slot(prefs,"face");

        float size=small?Math.min(w,h)*.74f:Math.min(w*.62f,h*.70f),s=size/120;
        c.save();c.translate((w-size)/2,small?(h-size)/2+size*.06f:h*.45f-size/2);c.scale(s,s);
        // Whole-body motion pivots near the feet, like the app's transform-origin: 50% 85%.
        float ty,tx=0,rot=0,sx=1,sy=1,hop=(now-hopStart)/650f;
        if(m.equals("cheer")){float f=(mt/.62f)%1;ty=keys(f,new float[]{0,.35f,.7f,.85f,1},new float[]{0,-16,0,-4,0})*PX;rot=keys(f,new float[]{0,.35f,.7f,1},new float[]{0,-4,0,0});}
        else if(hop<2){float f=hop%1;ty=keys(f,new float[]{0,.35f,.7f,.85f,1},new float[]{0,-13,0,-5,0})*PX;rot=keys(f,new float[]{0,.35f,.7f,1},new float[]{0,-3,0,0});}
        else if(listening){ty=0;rot=-2.5f*(float)Math.cos(2*Math.PI*t/2.1);}
        else if(m.equals("groove")){ty=-3*PX*Math.abs((float)Math.sin(Math.PI*t*2));rot=7*(float)Math.sin(Math.PI*t*2);}
        else if(m.equals("laugh")){ty=-2*PX*Math.abs((float)Math.sin(t*14));rot=2.5f*(float)Math.sin(t*22);}
        else if(m.equals("scared")){ty=7;tx=.9f*(float)Math.sin(t*70);}
        else if(m.equals("sad")){ty=4;rot=-2;}
        else if(m.equals("sleepy")){ty=1.5f;sy=1+.022f*(float)Math.sin(2*Math.PI*t/4.6);sx=2-sy;}
        else if(m.equals("stretch")){float f=keys(mt/2.6f,new float[]{0,.35f,.7f,1},new float[]{0,1,1,0});sy=1+.13f*f;sx=1-.07f*f;ty=0;}
        else if(m.equals("aww")){ty=-2*PX*(1-(float)Math.cos(2*Math.PI*t/2.2))/2;rot=3*(float)Math.sin(2*Math.PI*t/2.2);}
        else ty=-5*PX*(1-(float)Math.cos(2*Math.PI*t/2.8))/2;
        // a small nod with each syllable
        if(speaking&&hop>=2){ty-=1.6f*talk;sy*=1+.02f*talk;sx*=1-.012f*talk;}
        c.translate(60+tx,102+ty);c.rotate(rot);c.scale(sx,sy);c.translate(-60,-102);

        // feet, sprout, then the cover with its spine and pages clipped to the rounded corners
        oval(c,46,110,9,5,PUPIL);oval(c,74,110,9,5,PUPIL);
        if(!hidesSprout(hat)){path.reset();path.moveTo(60,17);path.lineTo(60,9);stroke(c,color(p,"stem","#2d7d46"),2.5f);
            int sprout=color(p,"sprout","#34a853");
            c.save();c.rotate(-28,54.5f,8);oval(c,54.5f,8,5.5f,3,sprout);c.restore();
            c.save();c.rotate(28,65.5f,8);oval(c,65.5f,8,5.5f,3,sprout);c.restore();}
        fill.setColor(color(p,"cover","#f97316"));c.drawPath(cover,fill);
        c.save();c.clipPath(cover);
        fill.setColor(color(p,"spine","#ea580c"));c.drawRect(22,16,36,108,fill);
        fill.setColor(color(p,"pages","#ffedd5"));c.drawRoundRect(30,94,92,108,6,6,fill);
        line.setColor(color(p,"pageLines","#fdba74"));line.setStrokeWidth(1.6f);c.drawLine(36,98,86,98,line);c.drawLine(36,102,86,102,line);
        c.restore();
        if(p.has("edge")){line.setColor(color(p,"edge","#c9a96e"));line.setStrokeWidth(2);c.drawPath(cover,line);}
        // hat (the grad cap's tassel swings from the board's corner, buddy-tassel 3.4s), then neckwear
        item(c,hat);if(hat.equals("grad")){c.save();c.rotate(.5f-7.5f*(float)Math.cos(2*Math.PI*t/3.4),85,13);item(c,"grad-tassel");c.restore();}
        item(c,neck);

        // blush: fuller while it chats, like the app's happy face
        boolean glow=speaking||m.equals("aww")||m.equals("laugh")||m.equals("cheer");
        fill.setColor(color(p,"blush","#fdba74"));fill.setAlpha(glow?255:204);
        float br=glow?6.5f:5.5f;c.drawCircle(36,71,br,fill);c.drawCircle(88,71,br,fill);fill.setAlpha(255);

        // eyes: the group blinks (scaleY about its centre); pupils glance aside while idle
        float blink=keys(cycle(t+1.3f,4.8f),new float[]{0,.91f,.93f,.95f,.97f,1},new float[]{1,1,.08f,.08f,1,1});
        // While talking it smiles with its eyes (^ ^) for a beat every few seconds.
        boolean beam=speaking&&cycle(t,3.6f)>.62f&&cycle(t,3.6f)<.84f;
        if(beam||m.equals("laugh")||m.equals("groove")||m.equals("cheer")&&((mt/.62f)%1)<.5f||m.equals("stretch")){happyEyes(c);}
        else if(m.equals("sleepy")){
            // Heavy lids: the top of each eye is covered by the cover colour, drooping further now and then.
            float lid=.55f+.3f*keys(cycle(t,7f),new float[]{0,.6f,.75f,.9f,1},new float[]{0,0,1,1,0});
            for(float x:new float[]{46,78}){oval(c,x,55,13,14,WHITE);circle(c,x+1,61,6.5f,PUPIL);fill.setColor(color(p,"cover","#f97316"));c.drawRect(x-14,40,x+14,41+28*lid,fill);path.reset();path.moveTo(x-12.5f,41+28*lid);path.quadTo(x,44+28*lid,x+12.5f,41+28*lid);stroke(c,INK,2.2f);}
        }else{
        c.save();c.scale(1,m.equals("scared")?1:blink,62,55);
        oval(c,46,55,13,14,WHITE);oval(c,78,55,13,14,WHITE);
        if(m.equals("scared")){float dx=3.5f*(float)Math.sin(t*5);circle(c,46+dx,55,4.6f,PUPIL);circle(c,78+dx,55,4.6f,PUPIL);circle(c,44.5f+dx,53.5f,1.5f,WHITE);circle(c,76.5f+dx,53.5f,1.5f,WHITE);}
        else if(m.equals("sad")){circle(c,46,62,6.5f,PUPIL);circle(c,78,62,6.5f,PUPIL);circle(c,44,59.5f,2.2f,WHITE);circle(c,76,59.5f,2.2f,WHITE);}
        else if(listening){circle(c,49.5f,52,7,PUPIL);circle(c,81.5f,52,7,PUPIL);circle(c,47,49.5f,2.5f,WHITE);circle(c,79,49.5f,2.5f,WHITE);circle(c,52,55,1.2f,WHITE);circle(c,84,55,1.2f,WHITE);}
        else if(reading){circle(c,49.5f,61.5f,6.5f,PUPIL);circle(c,76.5f,61.5f,6.5f,PUPIL);circle(c,47.5f,59.5f,2.2f,WHITE);circle(c,74.5f,59.5f,2.2f,WHITE);}
        else{float gx=speaking?0:keys(cycle(t,6.5f),new float[]{0,.5f,.56f,.64f,.7f,1},new float[]{0,0,3.5f,3.5f,0,0});
            c.translate(gx,0);circle(c,47.5f,56.5f,7,PUPIL);circle(c,79.5f,56.5f,7,PUPIL);circle(c,45,53.5f,2.5f,WHITE);circle(c,77,53.5f,2.5f,WHITE);circle(c,50.5f,59.5f,1.2f,WHITE);circle(c,82.5f,59.5f,1.2f,WHITE);}
        c.restore();
        }
        item(c,eyewear);

        // mouth
        if(m.equals("cheer")||m.equals("laugh")||m.equals("stretch")){
            // Wide open: a cheer or a laugh, or a big round yawn while stretching.
            path.reset();
            if(m.equals("stretch")){float o=keys(mt/2.6f,new float[]{0,.3f,.7f,1},new float[]{2,8,8,2});path.addOval(new RectF(56,74,68,76+o),Path.Direction.CW);}
            else{path.moveTo(50,75);path.quadTo(62,73.5f,74,75);path.quadTo(62,93,50,75);path.close();}
            fill.setColor(INK);c.drawPath(path,fill);c.save();c.clipPath(path);oval(c,62,m.equals("stretch")?84:85,5,3,TONGUE);c.restore();
        }else if(m.equals("sad")){path.reset();path.moveTo(54,82);path.quadTo(62,75,70,82);stroke(c,INK,4.2f);
            // one small tear
            float f=(mt%2.4f)/2.4f;fill.setColor(0xff7dd3fc);fill.setAlpha((int)(255*(1-f)));c.drawCircle(37,68+14*f,2.3f,fill);fill.setAlpha(255);}
        else if(m.equals("sleepy"))oval(c,62,79,2.6f,3.2f,INK);
        else if(m.equals("scared")){path.reset();path.moveTo(53,79);for(int i=1;i<=6;i++)path.lineTo(53+i*3,i%2==1?76.5f:79);stroke(c,INK,2.4f);}
        else if(speaking){
            // A small, soft mouth with smiling corners: a closed smile between words, a rounded "o"
            // with the tongue peeking on the big syllables.
            float hw=6+1.2f*talk,d=1.8f+5.2f*talk;
            path.reset();path.moveTo(62-hw,76.5f);path.cubicTo(62-hw*.45f,77.6f,62+hw*.45f,77.6f,62+hw,76.5f);path.cubicTo(62+hw,76.5f+d*1.33f,62-hw,76.5f+d*1.33f,62-hw,76.5f);path.close();
            fill.setColor(INK);c.drawPath(path,fill);stroke(c,INK,2.2f);
            if(d>3){c.save();c.clipPath(path);oval(c,62,76.5f+d-.2f,hw*.62f,2.4f,TONGUE);if(face.equals("splitteeth")&&d>3.5f)teeth(c,77);c.restore();}
        }else if(face.equals("splitteeth")){
            // Gap-toothed grin, which replaces the mood's mouth in the app.
            path.reset();path.moveTo(50.5f,75.5f);path.quadTo(62,88,73.5f,75.5f);path.close();fill.setColor(INK);c.drawPath(path,fill);stroke(c,INK,1.5f);
            oval(c,62,80.4f,4.6f,2.2f,TONGUE);teeth(c,75.6f);
        }else if(listening)circle(c,62,80,4.5f,INK);
        else{path.reset();path.moveTo(55,78);path.quadTo(62,84,69,78);stroke(c,INK,4.5f);}

        faceDetail(c,face);

        if(m.equals("scared")){
            // Peeking out from under a blanket pulled up to the eyes.
            path.reset();path.moveTo(10,72);path.quadTo(26,64,42,70);path.quadTo(60,75,78,69);path.quadTo(96,63,110,71);path.lineTo(112,122);path.lineTo(8,122);path.close();
            fill.setColor(0xff60a5fa);c.drawPath(path,fill);c.save();c.clipPath(path);line.setColor(0x66ffffff);line.setStrokeWidth(3);
            for(float x=14;x<112;x+=14)c.drawLine(x,60,x,124,line);for(float y=80;y<124;y+=14)c.drawLine(4,y,116,y,line);c.restore();
            line.setColor(0xff2563eb);line.setStrokeWidth(1.6f);c.drawPath(path,line);
            // fingers... well, page corners holding it
            oval(c,40,71,5,3.4f,color(p,"pages","#ffedd5"));oval(c,82,70,5,3.4f,color(p,"pages","#ffedd5"));
            float f=(mt%1.8f)/1.8f;fill.setColor(0xff7dd3fc);fill.setAlpha((int)(230*(1-f)));c.drawCircle(97,38+10*f,2.6f,fill);fill.setAlpha(255);
        }
        if(m.equals("cheer")){
            // Confetti falling around Buddy.
            int[] colors={0xfff97316,0xff38bdf8,0xfffde047,0xff34d399,0xfff472b6,scarfMain};
            for(int i=0;i<14;i++){float speed=.45f+(i%5)*.09f,f=((mt*speed)+i*.137f)%1;float x=4+((i*37)%112)+5*(float)Math.sin(t*3+i);float y=-6+f*120;
                fill.setColor(colors[i%colors.length]);fill.setAlpha((int)(255*Math.min(1,(1-f)*3)));c.save();c.rotate((t*200+i*40)%360,x,y);c.drawRect(x-2,y-1.2f,x+2,y+1.2f,fill);c.restore();}
            fill.setAlpha(255);
        }
        if(m.equals("sleepy")){
            for(int i=0;i<3;i++){float f=((t/3.2f)+i/3f)%1;int a=(int)(255*Math.sin(Math.PI*f));label(c,"z",92+f*18,14-f*26,7+f*7,(a<<24)|0xe0e7ff);}
        }
        if(m.equals("groove")){
            for(int i=0;i<2;i++){float f=((t/1.9f)+i*.5f)%1;fill.setColor(NOTE);fill.setAlpha((int)(255*(1-f)));line.setColor(NOTE);line.setAlpha((int)(255*(1-f)));
                float x=i==0?104:12,y=34-f*30;c.drawOval(x-4.5f,y-3,x+4.5f,y+3,fill);line.setStrokeWidth(2.2f);c.drawLine(x+4,y,x+4,y-13,line);}
            fill.setAlpha(255);line.setAlpha(255);
        }

        // listening: a ♪ drifts beside the sprout (the app's buddy-note, 1.6s)
        if(listening){float f=(1-(float)Math.cos(2*Math.PI*t/1.6))/2;int alpha=(int)(255*(.9f-.4f*f));
            c.save();c.translate(0,-7*PX*f);c.rotate(12*f,116,-4);
            fill.setColor(NOTE);fill.setAlpha(alpha);c.save();c.rotate(-22,111,4);c.drawOval(105,0,117,8,fill);c.restore();
            line.setColor(NOTE);line.setAlpha(alpha);line.setStrokeWidth(2.6f);c.drawLine(116,3,116,-17,line);
            path.reset();path.moveTo(116,-17);path.quadTo(124,-13,122,-5);c.drawPath(path,line);
            fill.setAlpha(255);line.setAlpha(255);c.restore();}
        c.restore();

        if(!small){float sp=getResources().getDisplayMetrics().scaledDensity;label(c,Duo.name(getContext()),w/2,h*.88f,sp*22,Color.rgb(244,248,251));label(c,"A FAMILIAR FACE. A NEW ADVENTURE.",w/2,h*.96f,sp*8,Color.rgb(138,157,172));}
        if(small)postInvalidateDelayed(33);else postInvalidateOnAnimation();
    }
}
