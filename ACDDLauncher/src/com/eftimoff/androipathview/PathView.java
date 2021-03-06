/*
 * ACDD Project
 * file PathView.java  is  part of ACCD
 * The MIT License (MIT)  Copyright (c) 2015 Bunny Blue,achellies.
 *
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 *
 */

package com.eftimoff.androipathview;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.Interpolator;

import org.acdd.launcher.R;

import java.util.ArrayList;
import java.util.List;

/**
 * PathView is an View that animate paths.
 */
public class PathView extends View {
    /**
     * Logging tag.
     */
    public static final String LOG_TAG = "PathView";
    /**
     * The paint for the path.
     */
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /**
     * Utils to catch the paths from the svg.
     */
    private final SvgUtils svgUtils = new SvgUtils(paint);
    /**
     * All the paths provided to the view. Both from Path and Svg.
     */
    private List<SvgUtils.SvgPath> paths = new ArrayList<SvgUtils.SvgPath>(0);
    /**
     * This is a lock before the view is redrawn
     * or resided it must be synchronized with this object.
     */
    private final Object mSvgLock = new Object();
    /**
     * Thread for working with the object above.
     */
    private Thread mLoader;

    /**
     * The svg image from the raw directory.
     */
    private int svgResourceId;
    /**
     * Object that build the animation for the path.
     */
    private AnimatorBuilder animatorBuilder;
    /**
     * The progress of the drawing.
     */
    private float progress = 0f;

    /**
     * If the used colors are from the svg or from the set color.
     */
    private boolean naturalColors;
    /**
     * If the view is filled with its natural colors after path drawing.
     */
    private boolean fillAfter;
    /**
     * The width of the view.
     */
    private int width;
    /**
     * The height of the view.
     */
    private int height;

    /**
     * Default constructor.
     *
     * @param context The Context of the application.
     */
    public PathView(Context context) {
        this(context, null);
    }

    /**
     * Default constructor.
     *
     * @param context The Context of the application.
     * @param attrs   attributes provided from the resources.
     */
    public PathView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Default constructor.
     *
     * @param context  The Context of the application.
     * @param attrs    attributes provided from the resources.
     * @param defStyle Default style.
     */
    public PathView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        paint.setStyle(Paint.Style.STROKE);
        getFromAttributes(context, attrs);
    }

    /**
     * Get all the fields from the attributes .
     *
     * @param context The Context of the application.
     * @param attrs   attributes provided from the resources.
     */
    private void getFromAttributes(Context context, AttributeSet attrs) {
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PathView);
        try {
            if (a != null) {
                paint.setColor(a.getColor(R.styleable.PathView_pathColor, 0xff00ff00));
                paint.setStrokeWidth(a.getFloat(R.styleable.PathView_pathWidth, 8.0f));
                svgResourceId = a.getResourceId(R.styleable.PathView_svg, 0);
            }
        } finally {
            if (a != null) {
                a.recycle();
            }
        }
    }

    /**
     * Set paths to be drawn and animated.
     *
     * @param paths - Paths that can be drawn.
     */
    public void setPaths(final List<Path> paths) {
        for (Path path : paths) {
            this.paths.add(new SvgUtils.SvgPath(path, paint));
        }
        synchronized (mSvgLock) {
            updatePathsPhaseLocked();
        }
    }

    /**
     * Set path to be drawn and animated.
     *
     * @param path - Paths that can be drawn.
     */
    public void setPath(final Path path) {
        paths.add(new SvgUtils.SvgPath(path, paint));
        synchronized (mSvgLock) {
            updatePathsPhaseLocked();
        }
    }
//    public void setFillTransparency(float BundlesInstaller) {
//        this.mFillTransparency = BundlesInstaller;
//        this.mPinPath = true;
//        synchronized (this.mLock) {
//            updatePathsFillTransparencyLocked();
//        }
//        invalidate();
//    }
    /**
     * Animate this property. It is the percentage of the path that is drawn.
     * It must be [0,1].
     *
     * @param percentage float the percentage of the path.
     */
    public void setPercentage(float percentage) {
        if (percentage < 0.0f || percentage > 1.0f) {
            throw new IllegalArgumentException("setPercentage not between 0.0f and 1.0f");
        }
        progress = percentage;
        synchronized (mSvgLock) {
            updatePathsPhaseLocked();
        }
        invalidate();
    }

    /**
     * This refreshes the paths before draw and resize.
     */
    private void updatePathsPhaseLocked() {
        final int count = paths.size();
        for (int i = 0; i < count; i++) {
            SvgUtils.SvgPath svgPath = paths.get(i);
            svgPath.path.reset();
            svgPath.measure.getSegment(0.0f, svgPath.length * progress, svgPath.path, true);
            // Required only for Android 4.4 and earlier
            svgPath.path.rLineTo(0.0f, 0.0f);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        synchronized (mSvgLock) {
            canvas.save();
            canvas.translate(getPaddingLeft(), getPaddingTop());
            final int count = paths.size();
            for (int i = 0; i < count; i++) {
                final SvgUtils.SvgPath svgPath = paths.get(i);
                final Path path = svgPath.path;
                final Paint paint1 = naturalColors ? svgPath.paint : paint;
                canvas.drawPath(path, paint1);
            }
            fillAfter(canvas);
            canvas.restore();
        }
    }

    /**
     * If there is svg , the user called setFillAfter(true) and the progress is finished.
     *
     * @param canvas Draw to this canvas.
     */
    private void fillAfter(final Canvas canvas) {
        if (svgResourceId != 0 && fillAfter && progress == 1f) {
            svgUtils.drawSvgAfter(canvas, width, height);
        }
    }

    @Override
    protected void onSizeChanged(final int w, final int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (mLoader != null) {
            try {
                mLoader.join();
            } catch (InterruptedException e) {
                Log.e(LOG_TAG, "Unexpected error", e);
            }
        }
        if (svgResourceId != 0) {
            mLoader = new Thread(new Runnable() {
                @Override
                public void run() {

                    svgUtils.load(getContext(), svgResourceId);

                    synchronized (mSvgLock) {
                        width = w - getPaddingLeft() - getPaddingRight();
                        height = h - getPaddingTop() - getPaddingBottom();
                        paths = svgUtils.getPathsForViewport(width, height);
                        updatePathsPhaseLocked();
                    }
                }
            }, "SVG Loader");
            mLoader.start();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (svgResourceId != 0) {
            int widthSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightSize = MeasureSpec.getSize(heightMeasureSpec);
            setMeasuredDimension(widthSize, heightSize);
            return;
        }

        int desiredWidth = 0;
        int desiredHeight = 0;
        final float strokeWidth = paint.getStrokeWidth() / 2;
        for (SvgUtils.SvgPath path : paths) {
            desiredWidth += path.bounds.left + path.bounds.width() + strokeWidth;
            desiredHeight += path.bounds.top + path.bounds.height() + strokeWidth;
        }
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(widthMeasureSpec);

        int measuredWidth, measuredHeight;

        if (widthMode == MeasureSpec.AT_MOST) {
            measuredWidth = desiredWidth;
        } else {
            measuredWidth = widthSize;
        }

        if (heightMode == MeasureSpec.AT_MOST) {
            measuredHeight = desiredHeight;
        } else {
            measuredHeight = heightSize;
        }

        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    /**
     * If the real svg need to be drawn after the path animation.
     *
     * @param fillAfter - boolean if the view needs to be filled after path animation.
     */
    public void setFillAfter(final boolean fillAfter) {
        this.fillAfter = fillAfter;
    }

    /**
     * If you want to use the colors from the svg.
     */
    public void useNaturalColors() {
        naturalColors = true;
    }

    /**
     * Animator for the paths of the view.
     *
     * @return The AnimatorBuilder to build the animation.
     */
    public AnimatorBuilder getPathAnimator() {
        if (animatorBuilder == null) {
            animatorBuilder = new AnimatorBuilder(this);
        }
        return animatorBuilder;
    }

    /**
     * Get the path color.
     *
     * @return The color of the paint.
     */
    public int getPathColor() {
        return paint.getColor();
    }

    /**
     * Set the path color.
     *
     * @param color -The color to set to the paint.
     */
    public void setPathColor(final int color) {
        paint.setColor(color);
    }

    /**
     * Get the path width.
     *
     * @return The width of the paint.
     */
    public float getPathWidth() {
        return paint.getStrokeWidth();
    }

    /**
     * Set the path width.
     *
     * @param width - The width of the path.
     */
    public void setPathWidth(final float width) {
        paint.setStrokeWidth(width);
    }

    /**
     * Get the svg resource id.
     *
     * @return The svg raw resource id.
     */
    public int getSvgResource() {
        return svgResourceId;
    }

    /**
     * Set the svg resource id.
     *
     * @param svgResource - The resource id of the raw svg.
     */
    public void setSvgResource(int svgResource) {
        svgResourceId = svgResource;
    }

    /**
     * Object for building the animation of the path of this view.
     */
    public static class AnimatorBuilder {
        /**
         * Duration of the animation.
         */
        private int duration = 350;
        /**
         * Interpolator for the time of the animation.
         */
        private Interpolator interpolator;
        /**
         * The delay before the animation.
         */
        private int delay = 0;
        /**
         * ObjectAnimator that constructs the animation.
         */
        private final ObjectAnimator anim;
        /**
         * Listener called before the animation.
         */
        private ListenerStart listenerStart;
        /**
         * Listener after the animation.
         */
        private ListenerEnd animationEnd;
        /**
         * Animation listener.
         */
        private PathViewAnimatorListener pathViewAnimatorListener;

        /**
         * Default constructor.
         *
         * @param pathView The view that must be animated.
         */
        public AnimatorBuilder(final PathView pathView) {
            anim = ObjectAnimator.ofFloat(pathView, "percentage", 0.0f, 1.0f);
        }

        /**
         * Set the duration of the animation.
         *
         * @param duration - The duration of the animation.
         * @return AnimatorBuilder.
         */
        public AnimatorBuilder duration(final int duration) {
            this.duration = duration;
            return this;
        }

        /**
         * Set the Interpolator.
         *
         * @param interpolator - Interpolator.
         * @return AnimatorBuilder.
         */
        public AnimatorBuilder interpolator(final Interpolator interpolator) {
            this.interpolator = interpolator;
            return this;
        }

        /**
         * The delay before the animation.
         *
         * @param delay - int the delay
         * @return AnimatorBuilder.
         */
        public AnimatorBuilder delay(final int delay) {
            this.delay = delay;
            return this;
        }

        /**
         * Set a listener before the start of the animation.
         *
         * @param listenerStart an interface called before the animation
         * @return AnimatorBuilder.
         */
        public AnimatorBuilder listenerStart(final ListenerStart listenerStart) {
            this.listenerStart = listenerStart;
            if (pathViewAnimatorListener == null) {
                pathViewAnimatorListener = new PathViewAnimatorListener();
                anim.addListener(pathViewAnimatorListener);
            }
            return this;
        }

        /**
         * Set a listener after of the animation.
         *
         * @param animationEnd an interface called after the animation
         * @return AnimatorBuilder.
         */
        public AnimatorBuilder listenerEnd(final ListenerEnd animationEnd) {
            this.animationEnd = animationEnd;
            if (pathViewAnimatorListener == null) {
                pathViewAnimatorListener = new PathViewAnimatorListener();
                anim.addListener(pathViewAnimatorListener);
            }
            return this;
        }

        /**
         * Starts the animation.
         */
        public void start() {
            anim.setDuration(duration);
            anim.setInterpolator(interpolator);
            anim.setStartDelay(delay);
            anim.setRepeatMode(ObjectAnimator.RESTART);
            anim.setRepeatCount(ObjectAnimator.INFINITE);
            anim.start();
        }

        /**
         * Animation listener to be able to provide callbacks for the caller.
         */
        private class PathViewAnimatorListener implements Animator.AnimatorListener {

            @Override
            public void onAnimationStart(Animator animation) {
                if (listenerStart != null) listenerStart.onAnimationStart();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (animationEnd != null) animationEnd.onAnimationEnd();
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        }

        /**
         * Called when the animation start.
         */
        public interface ListenerStart {
            /**
             * Called when the path animation start.
             */
            void onAnimationStart();
        }

        /**
         * Called when the animation end.
         */
        public interface ListenerEnd {
            /**
             * Called when the path animation end.
             */
            void onAnimationEnd();
        }
    }
}
