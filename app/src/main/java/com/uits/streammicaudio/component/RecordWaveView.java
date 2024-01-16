package com.uits.streammicaudio.component;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Xfermode;
import android.util.AttributeSet;
import android.util.SparseArray;

import androidx.core.content.ContextCompat;

import com.uits.streammicaudio.R;

import java.util.Random;
public class RecordWaveView extends RenderView {

    private int SAMPLING_SIZE = 64;
    private float OFFSET_SPEED = 500F;
    private float CIRCLE_SPEED = 150F;
    private float DEFAULT_CIRCLE_RADIUS;

    private final Paint paint = new Paint();

    {
        paint.setDither(true);
        paint.setAntiAlias(true);
    }

    private final Path firstPath = new Path();
    private final Path centerPath = new Path();
    private final Path secondPath = new Path();
    private float[] samplingX;
    private float[] mapX;
    private int width,height;
    private int centerHeight;
    private int amplitude;
    private final float[][] crestAndCrossPints = new float[10][];

    {
        for (int i = 0; i < 9; i++) {
            crestAndCrossPints[i] = new float[2];
        }
    }

    private final RectF rectF = new RectF();
    private final Xfermode xfermode = new PorterDuffXfermode(PorterDuff.Mode.SRC_IN);
    private int backGroundColor;
    private int centerPathColor;
    private int firstPathColor;
    private int secondPathColor;
    private boolean isShowBalls;
    private SparseArray<Double> recessionFuncs = new SparseArray<>();

    public RecordWaveView(Context context) {
        this(context,null);
    }

    public RecordWaveView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecordWaveView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        TypedArray t = getContext().obtainStyledAttributes(attrs, R.styleable.RecordWaveView);
        backGroundColor = t.getColor(R.styleable.RecordWaveView_backgroundColor,
                ContextCompat.getColor(getContext(),R.color.backgroundColor));
        firstPathColor = t.getColor(R.styleable.RecordWaveView_firstPathColor,
                ContextCompat.getColor(getContext(),R.color.firstPathColor));
        secondPathColor = t.getColor(R.styleable.RecordWaveView_secondPathColor,
                ContextCompat.getColor(getContext(),R.color.secondPathColor));
        centerPathColor = t.getColor(R.styleable.RecordWaveView_centerPathColor,
                ContextCompat.getColor(getContext(),R.color.centerPathColor));
        isShowBalls = t.getBoolean(R.styleable.RecordWaveView_showBalls, true);
        amplitude = t.getDimensionPixelSize(R.styleable.RecordWaveView_amplitude,0);
        SAMPLING_SIZE = t.getInt(R.styleable.RecordWaveView_ballSpeed,64);
        OFFSET_SPEED = t.getFloat(R.styleable.RecordWaveView_moveSpeed,500F);
        CIRCLE_SPEED = t.getFloat(R.styleable.RecordWaveView_ballSpeed,150F);
        DEFAULT_CIRCLE_RADIUS = dip2px(3);
        t.recycle();
    }

    @Override
    protected void onRender(Canvas canvas, long millisPassed) {
        super.onRender(canvas, millisPassed);
        if (null == samplingX){
            initDraw(canvas);
        }

        refreshAmplitude();

        //绘制背景
        canvas.drawColor(backGroundColor);

        //重置所有path并移动到起点
        firstPath.rewind();
        centerPath.rewind();
        secondPath.rewind();
        firstPath.moveTo(0,centerHeight);
        centerPath.moveTo(0,centerHeight);
        secondPath.moveTo(0,centerHeight);

        //当前时间的偏移量，通过该偏移量使每次绘制向右偏移，从而让曲线动起来
        float offset = millisPassed / OFFSET_SPEED;


        //波形函数的值，包括上一点，当前点，下一点
        float lastV,curV = 0, nextV = (float)(amplitude * calcValue(mapX[0], offset));
        //波形函数的绝对值，用于筛选波峰和交错点
        float absLastV, absCurV, absNextV;
        //上次的筛选点是波峰还是交错点
        boolean lastIsCrest = false;
        //筛选出的波峰和交错点的数量，包括起点和终点
        int crestAndCrossCount = 0;

        float x;
        float[] xy;
        for (int i = 0; i <= SAMPLING_SIZE; i++){
            //计算采样点的位置
            x = samplingX[i];
            lastV = curV;
            curV = nextV;
            //计算下一采样点的位置，并判断是否到终点
            nextV = i < SAMPLING_SIZE ? (float)(amplitude * calcValue(mapX[i + 1], offset)) : 0;

            //连接路径
            firstPath.lineTo(x, centerHeight + curV);
            secondPath.lineTo(x, centerHeight - curV);
            //中间曲线的振幅是上下曲线的1/5
            centerPath.lineTo(x, centerHeight - curV / 5F);

            //记录极值点
            absLastV = Math.abs(lastV);
            absCurV = Math.abs(curV);
            absNextV = Math.abs(nextV);

            if (i == 0 || i == SAMPLING_SIZE/*起点终点*/ || (lastIsCrest && absCurV < absLastV && absCurV < absNextV)/*上一个点为波峰，且该点是极小值点*/) {
                //交叉点
                xy = crestAndCrossPints[crestAndCrossCount++];
                xy[0] = x;
                xy[1] = 0;
                lastIsCrest = false;
            } else if (!lastIsCrest && absCurV > absLastV && absCurV > absNextV) {/*上一点是交叉点，且该点极大值*/
                //极大值点
                xy = crestAndCrossPints[crestAndCrossCount++];
                xy[0] = x;
                xy[1] = curV;
                lastIsCrest = true;
            }
        }

        //连接所有路径到终点
        firstPath.lineTo(width, centerHeight);
        secondPath.lineTo(width, centerHeight);
        centerPath.lineTo(width, centerHeight);

        //记录layer,将图层进行离屏缓存
        int saveCount = canvas.saveLayer(0, 0, width, height, null, Canvas.ALL_SAVE_FLAG);

        //填充上下两条正弦函数，为下一步混合交叠做准备
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(1);
        canvas.drawPath(firstPath, paint);
        canvas.drawPath(secondPath, paint);

        paint.setColor(firstPathColor);
        paint.setStyle(Paint.Style.FILL);
        paint.setXfermode(xfermode);


        float startX, crestY, endX;
        //根据上面计算的峰顶和交叉点的位置，绘制矩形
        for (int i = 2; i < crestAndCrossCount; i += 2){
            //每隔两个点绘制一个矩形
            startX = crestAndCrossPints[i - 2][0];
            crestY = crestAndCrossPints[i - 1][1];
            endX = crestAndCrossPints[i][0];

            //设置渐变矩形区域
            paint.setShader(new LinearGradient(0, centerHeight + crestY, 0,
                    centerHeight - crestY, firstPathColor, secondPathColor,
                    Shader.TileMode.CLAMP));
            rectF.set(startX, centerHeight + crestY, endX, centerHeight - crestY);
            canvas.drawRect(rectF, paint);
        }

        //释放画笔资源
        paint.setShader(null);
        paint.setXfermode(null);

        //叠加layer，因为使用了SRC_IN的模式所以只会保留波形渐变重合的地方
        canvas.restoreToCount(saveCount);

        //绘制上弦线
        paint.setStrokeWidth(3);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(firstPathColor);
        canvas.drawPath(firstPath, paint);

        //绘制下弦线
        paint.setColor(secondPathColor);
        canvas.drawPath(secondPath, paint);

        //绘制中间线
        paint.setColor(centerPathColor);
        canvas.drawPath(centerPath, paint);

        if (isShowBalls) {
            float circleOffset = millisPassed / CIRCLE_SPEED;
            drawCircleBalls(circleOffset, canvas);
        }

    }

    //根据分贝设置不同的振幅
    private int getAmplitude(int db) {
        if (db <= 40){
            return width >> 4;
        }else {
            return width >> 3;
        }
    }

    //初始化绘制参数
    private void initDraw(Canvas canvas) {
        width = canvas.getWidth();
        height = canvas.getHeight();
        centerHeight = height >> 1;
        //振幅为宽度的1/8
        //如果未设置振幅高度，则使用默认高度
        if (amplitude == 0) {
            amplitude = width >> 3;
        }

        //初始化采样点及映射

        //这里因为包括起点和终点，所以需要+1
        samplingX = new float[SAMPLING_SIZE + 1];
        mapX = new float[SAMPLING_SIZE + 1];
        //确定采样点之间的间距
        float gap = width / (float)SAMPLING_SIZE;
        //采样点的位置
        float x;
        for (int i = 0; i <= SAMPLING_SIZE; i++){
            x = i * gap;
            samplingX[i] = x;
            //将采样点映射到[-2，2]
            mapX[i] = (x / (float)width) * 4 - 2;
        }
    }


    /**
     * 计算波形函数中x对应的y值
     * 使用稀疏矩阵进行暂存计算好的衰减系数值，下次使用时直接查找，减少计算量
     * @param mapX   换算到[-2,2]之间的x值
     * @param offset 偏移量
     * @return
     */
    private double calcValue(float mapX, float offset) {
        int keyX = (int) (mapX*1000);
        offset %= 2;
        double sinFunc = Math.sin(0.75 * Math.PI * mapX - offset * Math.PI);
        double recessionFunc;
        if(recessionFuncs.indexOfKey(keyX) >=0 ){
            recessionFunc = recessionFuncs.get(keyX);
        }else {
            recessionFunc = Math.pow(4 / (4 + Math.pow(mapX, 4)), 2.5);
            recessionFuncs.put(keyX,recessionFunc);
        }
        return sinFunc * recessionFunc;
    }

    //绘制自由运动的小球
    private void drawCircleBalls(float speed, Canvas canvas){
        float x,y;
        //从左到右依次绘制

        paint.setColor(firstPathColor);
        paint.setStyle(Paint.Style.FILL);
        x = width / 6f + 40 * (float)(Math.sin(0.45 * speed - CIRCLE_SPEED * Math.PI));
        y = centerHeight + 50 * (float) Math.sin(speed);
        canvas.drawCircle(x,y,DEFAULT_CIRCLE_RADIUS,paint);

        paint.setColor(secondPathColor);
        x = 2 * width / 6f + 20 * (float) Math.sin(speed);
        y = centerHeight +(float) Math.sin(speed);
        canvas.drawCircle(x,y,DEFAULT_CIRCLE_RADIUS * 0.8f,paint);

        paint.setColor(secondPathColor);
        paint.setAlpha(60 + new Random().nextInt(40));
        x = 2.5f * width / 6f + 40 * (float)(Math.sin(0.35 * speed + CIRCLE_SPEED * Math.PI));
        y = centerHeight + 40 * (float) Math.sin(speed);
        canvas.drawCircle(x,y,DEFAULT_CIRCLE_RADIUS,paint);

        paint.setColor(firstPathColor);
        x = 3f * width / 6f + (float)(Math.cos(speed));
        y = centerHeight + 40 * (float) Math.sin(0.6f * speed);
        canvas.drawCircle(x,y,DEFAULT_CIRCLE_RADIUS * 0.7f,paint);

        paint.setColor(secondPathColor);
        x = 4 * width / 6f + 70 *(float)(Math.sin(speed));
        y = centerHeight + 10 * (float) Math.sin(speed);
        canvas.drawCircle(x,y,DEFAULT_CIRCLE_RADIUS * 0.5f,paint);

        paint.setColor(firstPathColor);
        x = 5.2f * width / 6f + 30 * (float)(Math.sin(0.21 * speed + CIRCLE_SPEED * Math.PI));
        y = centerHeight + 10 * (float) Math.cos(speed);
        canvas.drawCircle(x,y,DEFAULT_CIRCLE_RADIUS * 0.75f,paint);

        paint.setColor(secondPathColor);
        x = 5.5f * width / 6f + 60 * (float)(Math.sin(0.15 * speed - CIRCLE_SPEED * Math.PI));
        y = centerHeight + 50 * (float) Math.sin(speed);
        canvas.drawCircle(x,y,DEFAULT_CIRCLE_RADIUS * 0.7f,paint);

    }

    private int dip2px(float dpValue) {
        final float scale = getContext().getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    //设置音量分贝
    protected void setVolume(int db){
        amplitude = getAmplitude(db);
    }

    //通过音量分贝更新振幅
    protected void refreshAmplitude(){
    }

}