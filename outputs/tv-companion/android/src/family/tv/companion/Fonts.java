package family.tv.companion;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import java.util.HashMap;

/** DuoBook's rounded Quicksand font (variable weight 300–700) for everything Buddy shows. */
final class Fonts {
    private static final HashMap<Integer,Typeface> cache=new HashMap<>();
    static synchronized Typeface get(Context c,int weight){
        Typeface t=cache.get(weight);
        if(t==null){try{t=new Typeface.Builder(c.getAssets(),"fonts/Quicksand.ttf").setFontVariationSettings("'wght' "+weight).build();}catch(Exception e){t=null;}
            if(t==null)t=Typeface.create("sans-serif",weight>=600?Typeface.BOLD:Typeface.NORMAL);cache.put(weight,t);}
        return t;
    }
    /** Sets Quicksand on every text in a view tree; bold text gets the heavier weight. */
    static void apply(View v,int weight){
        if(v instanceof TextView){TextView t=(TextView)v;boolean bold=t.getTypeface()!=null&&t.getTypeface().isBold();t.setTypeface(get(v.getContext(),bold?700:weight));}
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)apply(g.getChildAt(i),weight);}
    }
    /** Shows a dialog in Quicksand, including list rows created later while scrolling. */
    static AlertDialog show(AlertDialog.Builder b){
        AlertDialog d=b.show();apply(d.getWindow().getDecorView(),600);
        ListView list=d.getListView();
        // DuoBook rows: rounded, with an orange outline on the focused one.
        int row=d.getContext().getResources().getIdentifier(Duo.night(d.getContext())?"duo_row_night":"duo_row","drawable",d.getContext().getPackageName());
        if(list!=null){list.setDivider(null);if(row!=0)list.setSelector(row);list.setDrawSelectorOnTop(false);
            list.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener(){public void onChildViewAdded(View p,View child){apply(child,600);}public void onChildViewRemoved(View p,View child){}});}
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE}){android.widget.Button btn=d.getButton(which);if(btn!=null){btn.setTextColor(Duo.night(d.getContext())?0xfffb923c:0xffea580c);btn.setTypeface(get(d.getContext(),700));}}
        return d;
    }
    /** An AlertDialog builder in DuoBook's selected, rounded style. */
    static AlertDialog.Builder dialog(Context c){
        int theme=c.getResources().getIdentifier(Duo.night(c)?"DuoNightDialog":"DuoDialog","style",c.getPackageName());
        return theme==0?new AlertDialog.Builder(c):new AlertDialog.Builder(c,theme);
    }
}
