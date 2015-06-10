package com.example.mtextview;

import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.TextView;

/**
 * @author huangwei YAO
 * @功能 图文混排TextView，请使用{@link #setMText(CharSequence)}
 * @2014年5月27日
 * @下午5:29:27
 */
public class MTextView extends TextView {

    private static final String TAG = "MTextView";

    /**
     * 缓存测量过的数据
     */
    private static HashMap<String, SoftReference<MeasuredData>> measuredData = new HashMap<String, SoftReference<MeasuredData>>();
    private static int hashIndex = 0;
    /**
     * 存储当前文本内容，每个item为一行
     */
    ArrayList<LINE> contentList = new ArrayList<LINE>();
    private Context context;
    /**
     * 用于测量字符宽度
     */
    private TextPaint paint = new TextPaint();
    /**
     * 用于测量span高度
     */
    private Paint.FontMetricsInt mSpanFmInt = new Paint.FontMetricsInt();
    /**
     * 临时使用,以免在onDraw中反复生产新对象
     */
    private FontMetrics mFontMetrics = new FontMetrics();

    //	private float lineSpacingMult = 0.5f;
    private int textColor = Color.BLACK;
    //行距
    private float lineSpacing;
    private int lineSpacingDP = 3;

    /**最大行数*/
    private int maxLine = Integer.MAX_VALUE;
    /**
     * 段间距,-1为默认
     */
    private int paragraphSpacing = -1;
    /**
     * 最大宽度
     */
    private int maxWidth;
    /**
     * 只有一行时的宽度
     */
    private int oneLineWidth = -1;
    /**
     * 已绘的行中最宽的一行的宽度
     */
    private float lineWidthMax = -1;
    /**
     * 存储当前文本内容,每个item为一个字符或者一个SpanObject
     */
    private ArrayList<Object> obList = new ArrayList<Object>();
    /**
     * 是否使用默认{@link #onMeasure(int, int)}和{@link #onDraw(Canvas)}
     */
    private boolean useDefault = false;
    protected CharSequence text = "";
    /**
     * text的id,measuredData会缓存text绘制前测量的高度，但是同样的text需要绘制的布局也许不同，例如同一个text，一个是右侧无图的，另一个是有图的，复用就有问题
     */
    protected String textId;

    private int minHeight;
    /**
     * 用以获取屏幕高宽
     */
    private DisplayMetrics displayMetrics;
    /**
     * {@link BackgroundColorSpan}用
     */
    private Paint textBgColorPaint = new Paint();
    /**
     * {@link BackgroundColorSpan}用
     */
    private Rect textBgColorRect = new Rect();

    public MTextView(Context context) {
        super(context);
        init(context,null);
    }

    public MTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public MTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }


    public void init(Context context, AttributeSet attrs) {
        this.context = context;
        paint.setAntiAlias(true);
        lineSpacing = dip2px(context, lineSpacingDP);
        minHeight = dip2px(context, 30);

        displayMetrics = new DisplayMetrics();

        try{
            //通过反射获取maxlines
            Class ownerClass = this.getClass();
            ownerClass = ownerClass.getSuperclass();

            Field field = ownerClass.getDeclaredField("mMaximum");
            field.setAccessible(true);

            maxLine = field.getInt(this);
            if (maxLine < 1){
                //应该不会出现这种情况
                maxLine = 1;
            }

        }catch (Exception e){
            Log.w(TAG, "反射异常", e);
//            maxLine = 2;
        }


    }

    public static int px2sp(Context context, float pxValue) {
        final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
        return (int) (pxValue / fontScale + 0.5f);
    }

    /**
     * 根据手机的分辨率从 dp 的单位 转成为 px(像素)
     */
    public static int dip2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    @Override
    public void setMaxWidth(int maxpixels) {
        super.setMaxWidth(maxpixels);
        maxWidth = maxpixels;
    }

    @Override
    public void setMinHeight(int minHeight) {
        super.setMinHeight(minHeight);
        this.minHeight = minHeight;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (useDefault) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int width = 0, height = 0;

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        switch (widthMode) {
            case MeasureSpec.EXACTLY:
                width = widthSize;
                break;
            case MeasureSpec.AT_MOST:
                width = widthSize;
                break;
            case MeasureSpec.UNSPECIFIED:

                ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                width = displayMetrics.widthPixels;
                break;
            default:
                break;
        }
        if (maxWidth > 0) {
            width = Math.min(width, maxWidth);
        }

        paint.setTextSize(this.getTextSize());
        paint.setColor(textColor);
        int realHeight = measureContentHeight(width);

        //如果实际行宽少于预定的宽度，减少行宽以使其内容横向居中
        int leftPadding = getCompoundPaddingLeft();
        int rightPadding = getCompoundPaddingRight();

        //为什么要取行宽的最小值？
//        width = Math.min(width, (int) lineWidthMax + leftPadding + rightPadding);

        //为什么单行时要取文字实际的行宽为TextView的宽？
//        if (oneLineWidth > -1) {
//            //把一行文字的实际宽度赋值给width
//            width = oneLineWidth;
//        }
        switch (heightMode) {
            case MeasureSpec.EXACTLY:
                height = heightSize;
                break;
            case MeasureSpec.AT_MOST:
                height = realHeight;
                break;
            case MeasureSpec.UNSPECIFIED:
                height = realHeight;
                break;
            default:
                break;
        }

        height += getCompoundPaddingTop() + getCompoundPaddingBottom();

        height = Math.max(height, minHeight);

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (useDefault) {
            super.onDraw(canvas);
            return;
        }
        if (contentList.isEmpty()) {
            return;
        }
        int width;

        Object ob;

        int leftPadding = getCompoundPaddingLeft();
        int topPadding = getCompoundPaddingTop();

        //行高去掉行间距
//        float height = 0 + topPadding + lineSpacing;
        float height = 0 + topPadding;
        //只有一行时 //只有一行的时候，oneLineWidth 不等于 -1
        if (oneLineWidth != -1) {
            height = getMeasuredHeight() / 2 - contentList.get(0).height / 2;
        }

        //为什么color要在这个onDraw方法里面设置才行？onMeasure方法中设置无效
        //不确定是缓存等原因，造成调用setText方法后，text的字体颜色不会被改变，需要在onDraw中再设置一次
        paint.setColor(textColor);

        for (LINE aContentList : contentList) {
            //绘制一行
            float realDrawedWidth = leftPadding;
            /** 是否换新段落*/
            boolean newParagraph = false;
            for (int j = 0; j < aContentList.line.size(); j++) {
                ob = aContentList.line.get(j);
                width = aContentList.widthList.get(j);

                paint.getFontMetrics(mFontMetrics);
                float x = realDrawedWidth;
                // 当前heigh + 一行文字的高度 - baseline下方的高度
                float y = height + aContentList.height - paint.getFontMetrics().descent;
                float top = y - aContentList.height;
                float bottom = y + mFontMetrics.descent;
                if (ob instanceof String) {
                    canvas.drawText((String) ob, realDrawedWidth, y, paint);
                    realDrawedWidth += width;
                    if(((String)ob).endsWith("\n") && j == aContentList.line.size()-1){
                        newParagraph = true;
                    }
                } else if (ob instanceof SpanObject) {
                    Object span = ((SpanObject) ob).span;
                    if (span instanceof DynamicDrawableSpan) {

                        int start = ((Spannable) text).getSpanStart(span);
                        int end = ((Spannable) text).getSpanEnd(span);
                        ((DynamicDrawableSpan) span).draw(canvas, text, start, end, (int) x, (int) top, (int) y, (int) bottom, paint);
                        realDrawedWidth += width;
                    } else if (span instanceof BackgroundColorSpan) {

                        textBgColorPaint.setColor(((BackgroundColorSpan) span).getBackgroundColor());
                        textBgColorPaint.setStyle(Style.FILL);
                        textBgColorRect.left = (int) realDrawedWidth;
                        int textHeight = (int) getTextSize();
                        textBgColorRect.top = (int) (height + aContentList.height - textHeight - mFontMetrics.descent);
                        textBgColorRect.right = textBgColorRect.left + width;
                        textBgColorRect.bottom = (int) (height + aContentList.height + lineSpacing - mFontMetrics.descent);
                        canvas.drawRect(textBgColorRect, textBgColorPaint);
                        canvas.drawText(((SpanObject) ob).source.toString(), realDrawedWidth, height + aContentList.height - mFontMetrics.descent, paint);
                        realDrawedWidth += width;
                    } else {
                        //做字符串处理
                        canvas.drawText(((SpanObject) ob).source.toString(), realDrawedWidth, height + aContentList.height - mFontMetrics.descent, paint);
                        realDrawedWidth += width;
                    }
                }

            }
            //如果要绘制段间距
            if(newParagraph){
                height += aContentList.height + paragraphSpacing;
            }else{
                height += aContentList.height + lineSpacing;
            }
        }

    }

    @Override
    public void setTextColor(int color) {
        super.setTextColor(color);
        textColor = color;
    }

    /**
     * 用于带ImageSpan的文本内容所占高度测量
     *
     * @param width 预定的宽度
     * @return 所需的高度
     */
    private int measureContentHeight(int width) {
        int cachedHeight = getCachedData(text.toString(), width);

        if (cachedHeight > 0) {
            return cachedHeight;
        }



        float textSize = this.getTextSize();
        FontMetrics fontMetrics = paint.getFontMetrics();
        //行高，字体矩阵的 bottom - top
        float lineHeight = fontMetrics.bottom - fontMetrics.top;
        //计算出的所需高度 //先把行间距赋值给TextView高干嘛？
//        float height = lineSpacing;
        float height = 0;

        int leftPadding = getCompoundPaddingLeft();
        int rightPadding = getCompoundPaddingRight();

        float drawedWidth = 0;

        boolean splitFlag = false;//BackgroundColorSpan拆分用

        //减去 paddingleft 和 paddingright
        //width是父View给子View分配的宽度
        width = width - leftPadding - rightPadding;//获取文字的实际显示区域

        oneLineWidth = -1;

        contentList.clear();

//        StringBuilder sb;

        LINE line = new LINE();

        for (int i = 0; i < obList.size(); i++) {
            Object ob = obList.get(i);
            // 已绘的宽度
            float obWidth = 0;//一个字符的宽度
            float obHeight = 0;//一个字符的高度(应该包含baseline那块高度？)

            if (ob instanceof String) {
                obWidth = paint.measureText((String) ob);
                obHeight = textSize;
                if ("\n".equals(ob)) {
                    //遇到"\n"则换行
                    //除了前面的文字，这一行剩下的空间都是“\n”的
                    obWidth = width - drawedWidth;
                }
            } else if (ob instanceof SpanObject) {
                Object span = ((SpanObject) ob).span;
                if (span instanceof DynamicDrawableSpan) {
                    int start = ((Spannable) text).getSpanStart(span);
                    int end = ((Spannable) text).getSpanEnd(span);
                    obWidth = ((DynamicDrawableSpan) span).getSize(getPaint(), text, start, end, mSpanFmInt);
                    //top是基准线上方高度，负值， bottom是基准线下方高度，正直，所以整个span高度是绝对值相加
                    obHeight = Math.abs(mSpanFmInt.top) + Math.abs(mSpanFmInt.bottom);
                    if (obHeight > lineHeight) {
                        //行高和span的高取最大值
                        lineHeight = obHeight;
                    }
                } else if (span instanceof BackgroundColorSpan) {
                    String str = ((SpanObject) ob).source.toString();
                    obWidth = paint.measureText(str);
                    obHeight = textSize;

                    //如果太长,拆分
                    int k = str.length() - 1;
                    while (width - drawedWidth < obWidth) {
                        obWidth = paint.measureText(str.substring(0, k--));
                    }
                    if (k < str.length() - 1) {
                        splitFlag = true;
                        SpanObject so1 = new SpanObject();
                        so1.start = ((SpanObject) ob).start;
                        so1.end = so1.start + k;
                        so1.source = str.substring(0, k + 1);
                        so1.span = ((SpanObject) ob).span;

                        SpanObject so2 = new SpanObject();
                        so2.start = so1.end;
                        so2.end = ((SpanObject) ob).end;
                        so2.source = str.substring(k + 1, str.length());
                        so2.span = ((SpanObject) ob).span;

                        ob = so1;
                        obList.set(i, so2);
                        i--;
                    }
                } else {
                    //做字符串处理
                    String str = ((SpanObject) ob).source.toString();
                    obWidth = paint.measureText(str);
                    obHeight = textSize;
                }
            }

            //这一行满了，存入contentList,新起一行
            if (width - drawedWidth < obWidth || splitFlag) {
                //行宽 - 已绘制的宽度 < 当前要绘制的字符或span的宽度
                splitFlag = false;
                contentList.add(line);

                if (drawedWidth > lineWidthMax) {
                    lineWidthMax = drawedWidth;
                }
                drawedWidth = 0;

                //判断行数的限制
//                int maxLines = getMaxLines();
                if (contentList.size() == maxLine){
                    //当前行数和最大行数相等
                    //最后一行的最后一个字符设置为…
                    Object lastOb = line.line.get(line.line.size()-1);

                    if (lastOb instanceof String) {
                        String str = ((String) lastOb);
                        lastOb = str.substring(0,str.length()-1).concat("…");


                    } else if (lastOb instanceof SpanObject) {
                        Object span = ((SpanObject) lastOb).span;
                        if (span instanceof DynamicDrawableSpan) {
                            lastOb = "…";
                        } else if (span instanceof BackgroundColorSpan) {
                            lastOb = "…";
                        } else {
                            //做字符串处理
                            String str = ((SpanObject) lastOb).source.toString();
                            lastOb = str.substring(0,str.length()-1).concat("…");
                        }
                    }

                    line.line.set(line.line.size()-1,lastOb);

                    //把当前字符后面的字符都清空
                    for (int k = i+1, l = obList.size();k < l;k++){
                        obList.remove(obList.size()-1);
                    }

                }else{
                    //没有超过最大行数
                    //判断是否有分段
                    int objNum = line.line.size();
                    if (paragraphSpacing > 0
                            && objNum > 0
                            && line.line.get(objNum - 1) instanceof String
                            && "\n".equals(line.line.get(objNum - 1))) {
                        height += line.height + paragraphSpacing;
                    } else {
                        height += line.height + lineSpacing;
//                    height += line.height;
                    }

                    //当前行剩余宽度，已经小于当前字符宽度，重新创建一行
                    line = new LINE();
                }


                lineHeight = obHeight;

            }else{
                //这一行剩余的宽度够这个字符
                //obWidth是每个字符的宽度，drawedWith是已经绘制的字符的宽度
                drawedWidth += obWidth;

                if (ob instanceof String && line.line.size() > 0 && (line.line.get(line.line.size() - 1) instanceof String)) {
                    //当前字符是一个String，并且当前已经绘制的最后一个字符也是String
                    //把相连的String字符，变成一个String存到Line对象中
                    int size = line.line.size();
                    StringBuilder sb = new StringBuilder();
                    sb.append(line.line.get(size - 1));
                    sb.append(ob);
                    ob = sb.toString();
                    obWidth = obWidth + line.widthList.get(size - 1);
                    line.line.set(size - 1, ob);
                    line.widthList.set(size - 1, (int) obWidth);
                    line.height = (int) lineHeight;
                } else {
                    line.line.add(ob);
                    line.widthList.add((int) obWidth);
                    line.height = (int) lineHeight;
                }

            }



        }

        //所有字符已经添加进Line,并且都加进contentList

        if (drawedWidth > lineWidthMax) {
            lineWidthMax = drawedWidth;
        }

        if (line != null && line.line.size() > 0) {
            //多行 //最后一行？干嘛要加行间距？
            contentList.add(line);
//            height += lineHeight + lineSpacing;
            height += lineHeight;
        }
        if (contentList.size() <= 1) {
            //一行或0行的情况？
            oneLineWidth = (int) drawedWidth + leftPadding + rightPadding;
            //单行干嘛要给上下加行间距
//            height = lineSpacing + lineHeight + lineSpacing;
            height = lineHeight;
        }

        cacheData(width, (int) height);
        return (int) height;
    }

    /**
     * 获取缓存的测量数据，避免多次重复测量
     *
     * @param text
     * @param width
     * @return height
     */
    @SuppressWarnings("unchecked")
    private int getCachedData(String text, int width) {
        if (textId == null){
            textId = "";
        }
        SoftReference<MeasuredData> cache = measuredData.get(textId + text);
        if (cache == null) {
            return -1;
        }
        MeasuredData md = cache.get();
        if (md != null && md.textSize == this.getTextSize() && width == md.width) {
            lineWidthMax = md.lineWidthMax;
            contentList = (ArrayList<LINE>) md.contentList.clone();
            oneLineWidth = md.oneLineWidth;

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < contentList.size(); i++) {
                LINE line = contentList.get(i);
                sb.append(line.toString());
            }
            return md.measuredHeight;
        } else {
            return -1;
        }
    }

    /**
     * 缓存已测量的数据
     *
     * @param width
     * @param height
     */
    @SuppressWarnings("unchecked")
    private void cacheData(int width, int height) {
        MeasuredData md = new MeasuredData();
        md.contentList = (ArrayList<LINE>) contentList.clone();
        md.textSize = this.getTextSize();
        md.lineWidthMax = lineWidthMax;
        md.oneLineWidth = oneLineWidth;
        md.measuredHeight = height;
        md.width = width;
        md.hashIndex = ++hashIndex;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < contentList.size(); i++) {
            LINE line = contentList.get(i);
            sb.append(line.toString());
        }

        SoftReference<MeasuredData> cache = new SoftReference<MeasuredData>(md);
        if (textId == null){
            textId = "";
        }
        measuredData.put(textId + text.toString(), cache);
    }

    /**
     * 无id
     *
     * @param cs
     */
    public void setMText(CharSequence cs) {
        setMText(null,cs);
    }
    /**
     * 用本函数代替{@link #setText(CharSequence)}
     *
     * @param cs
     */
    public void setMText(String textId, CharSequence cs) {
        if (useDefault){
            setText(cs);
            return;
        }
        this.textId = textId;
        text = cs;

        obList.clear();

        //把cs全部转换为SpanObject添加进isList
        ArrayList<SpanObject> isList = new ArrayList<SpanObject>();
        useDefault = false;

        if (cs instanceof Spannable) {
            CharacterStyle[] spans = ((Spannable) cs).getSpans(0, cs.length(), CharacterStyle.class);
            for (int i = 0; i < spans.length; i++) {
                //获取每个span的起始index,和结尾index ?
                int s = ((Spannable) cs).getSpanStart(spans[i]);
                int e = ((Spannable) cs).getSpanEnd(spans[i]);
                SpanObject iS = new SpanObject();
                iS.span = spans[i];
                iS.start = s;
                iS.end = e;
                iS.source = cs.subSequence(s, e);
                isList.add(iS);
            }
        }

        //对span进行排序，以免不同种类的span位置错乱 //为毛会错乱呢？
        SpanObject[] spanArray = new SpanObject[isList.size()];
        isList.toArray(spanArray);
        Arrays.sort(spanArray, 0, spanArray.length, new SpanObjectComparator());
        isList.clear();
        for (int i = 0; i < spanArray.length; i++) {
            //把排序后的SpanObject再添加进list
            isList.add(spanArray[i]);
        }

        String str = cs.toString();

        //添加SpanObject 和 单字符到obList中，一个元素就是一个字符或者SpanObject
        for (int i = 0, j = 0; i < cs.length(); ) {
            if (j < isList.size()) {
                SpanObject is = isList.get(j);
                if (i < is.start) {
                    Integer cp = str.codePointAt(i);
                    if (Character.isSupplementaryCodePoint(cp)) {
                        //支持增补字符
                        i += 2;
                    } else {
                        i++;
                    }

                    obList.add(new String(Character.toChars(cp)));

                } else if (i >= is.start) {
                    obList.add(is);
                    j++;
                    i = is.end;
                }
            } else {
                Integer cp = str.codePointAt(i);
                if (Character.isSupplementaryCodePoint(cp)) {
                    i += 2;
                } else {
                    i++;
                }

                obList.add(new String(Character.toChars(cp)));
            }
        }

        requestLayout();
    }

    public void setUseDefault(boolean useDefault) {
        this.useDefault = useDefault;
        if (useDefault) {
            this.setText(text);
            this.setTextColor(textColor);
        }
    }

    /**
     * 设置行距
     *
     * @param lineSpacingDP 行距，单位dp
     */
    public void setLineSpacingDP(int lineSpacingDP) {
        this.lineSpacingDP = lineSpacingDP;
        lineSpacing = dip2px(context, lineSpacingDP);
    }

    public void setParagraphSpacingDP(int paragraphSpacingDP) {
        paragraphSpacing = dip2px(context, paragraphSpacingDP);
    }

    /**
     * 获取行距
     *
     * @return 行距，单位dp
     */
    public int getLineSpacingDP() {
        return lineSpacingDP;
    }

    /**
     * @author huangwei
     * @功能: 存储Span对象及相关信息
     * @2014年5月27日
     * @下午5:21:37
     */
    class SpanObject {
        public Object span;
        public int start;
        public int end;
        public CharSequence source;//代表span的源charsequence，例如 代表“热”标签的 [LABEL]
    }

    /**
     * @author huangwei
     * @功能: 对SpanObject进行排序
     * @2014年6月4日
     * @下午5:21:30
     */
    class SpanObjectComparator implements Comparator<SpanObject> {
        @Override
        public int compare(SpanObject lhs, SpanObject rhs) {

            return lhs.start - rhs.start;
        }
    }

    /**
     * @author huangwei
     * @功能: 存储测量好的一行数据
     * @2014年5月27日
     * @下午5:22:12
     */
    class LINE {
        //每个字符的对象
        public ArrayList<Object> line = new ArrayList<Object>();
        //每个字符的宽度
        public ArrayList<Integer> widthList = new ArrayList<Integer>();
        public float height;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("height:" + height + "   ");
            for (int i = 0; i < line.size(); i++) {
                sb.append(line.get(i) + ":" + widthList.get(i));
            }
            return sb.toString();
        }
    }

    /**
     * @author huangwei
     * @功能: 缓存的数据
     * @2014年5月27日
     * @下午5:22:25
     */
    class MeasuredData {
        public int measuredHeight;
        public float textSize;
        public int width;
        public float lineWidthMax;
        public int oneLineWidth;
        public int hashIndex;
        ArrayList<LINE> contentList;
    }
}