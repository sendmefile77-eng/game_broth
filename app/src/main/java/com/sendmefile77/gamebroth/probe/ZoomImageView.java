package com.sendmefile77.gamebroth.probe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/** Bounded native image zoom, with gallery swipes when the image is fitted. */
final class ZoomImageView extends ImageView {
  private final Matrix matrix=new Matrix();
  private final ScaleGestureDetector pinch;
  private final GestureDetector gestures;
  private Bitmap bitmap;
  private float zoom=1,base=1,x,y;
  private final java.util.function.IntConsumer navigate;
  ZoomImageView(Context context,java.util.function.IntConsumer navigate){super(context);this.navigate=navigate;setScaleType(ScaleType.MATRIX);setContentDescription("Иллюстрация. Два касания — увеличить, свайп — следующий кадр");
    pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){float next=Math.max(1,Math.min(5,zoom*d.getScaleFactor())),factor=next/zoom;x=d.getFocusX()-(d.getFocusX()-x)*factor;y=d.getFocusY()-(d.getFocusY()-y)*factor;zoom=next;apply();return true;}});
    gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
      @Override public boolean onDown(MotionEvent e){return true;}
      @Override public boolean onDoubleTap(MotionEvent e){zoom=zoom>1?1:2;fitPosition();apply();return true;}
      @Override public boolean onScroll(MotionEvent first,MotionEvent next,float dx,float dy){if(zoom>1&&!pinch.isInProgress()){x-=dx;y-=dy;apply();}return true;}
      @Override public boolean onFling(MotionEvent a,MotionEvent b,float vx,float vy){if(zoom<=1.01f&&a!=null&&Math.abs(b.getX()-a.getX())>80&&Math.abs(vx)>Math.abs(vy)){navigate.accept(b.getX()<a.getX()?1:-1);return true;}return false;}
    });
  }
  void bitmap(Bitmap image){bitmap=image;setImageBitmap(image);zoom=1;fit();}
  @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){super.onSizeChanged(w,h,oldW,oldH);fit();}
  private void fit(){if(bitmap==null||getWidth()==0||getHeight()==0)return;base=Math.min((float)getWidth()/bitmap.getWidth(),(float)getHeight()/bitmap.getHeight());fitPosition();apply();}
  private void fitPosition(){if(bitmap==null)return;x=(getWidth()-bitmap.getWidth()*base*zoom)/2;y=(getHeight()-bitmap.getHeight()*base*zoom)/2;}
  private void apply(){if(bitmap==null)return;float w=bitmap.getWidth()*base*zoom,h=bitmap.getHeight()*base*zoom;
    x=w<=getWidth()?(getWidth()-w)/2:Math.max(getWidth()-w,Math.min(0,x));y=h<=getHeight()?(getHeight()-h)/2:Math.max(getHeight()-h,Math.min(0,y));matrix.reset();matrix.postScale(base*zoom,base*zoom);matrix.postTranslate(x,y);setImageMatrix(matrix);}
  @Override public boolean onTouchEvent(MotionEvent e){pinch.onTouchEvent(e);gestures.onTouchEvent(e);if(e.getAction()==MotionEvent.ACTION_UP)performClick();return true;}
  @Override public boolean performClick(){super.performClick();return true;}
}
