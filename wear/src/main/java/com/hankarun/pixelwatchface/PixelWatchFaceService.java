/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hankarun.pixelwatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class PixelWatchFaceService extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBackgroundPaint;
        Paint mTextPaint;

        boolean mAmbient;

        Time mTime;

        float mXOffset;
        float mYOffset;

        Bitmap mBackgroundBitmap;
        Paint mTickAndCirclePaint;
        Paint mTickAndCirclePaint1;

        Bitmap bmpsecond[];
        Bitmap bmphour[];
        Bitmap bmpminute[];

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(PixelWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = PixelWatchFaceService.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.digital_background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mTickAndCirclePaint = new Paint();
            mTickAndCirclePaint.setColor(resources.getColor(R.color.digital_text));
            mTickAndCirclePaint.setAntiAlias(true);
            mTickAndCirclePaint.setStyle(Paint.Style.STROKE);

            mTickAndCirclePaint1 = new Paint();
            mTickAndCirclePaint1.setColor(resources.getColor(R.color.red));
            mTickAndCirclePaint1.setAntiAlias(true);
            mTickAndCirclePaint1.setStyle(Paint.Style.STROKE);

            mTime = new Time();

            bmpsecond = new Bitmap[4];
            bmpsecond[0] = BitmapFactory.decodeResource(getResources(), R.drawable.sag);
            bmpsecond[1] = BitmapFactory.decodeResource(getResources(), R.drawable.orta);
            bmpsecond[2] = BitmapFactory.decodeResource(getResources(), R.drawable.sol);
            bmpsecond[3] = BitmapFactory.decodeResource(getResources(), R.drawable.orta);

            bmphour = new Bitmap[2];
            bmphour[0] = BitmapFactory.decodeResource(getResources(), R.drawable.girlrun);
            bmphour[1] = BitmapFactory.decodeResource(getResources(), R.drawable.girlstand);

            bmpminute = new Bitmap[2];
            bmpminute[0] = BitmapFactory.decodeResource(getResources(), R.drawable.runman);
            bmpminute[1] = BitmapFactory.decodeResource(getResources(), R.drawable.stayman);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            PixelWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            PixelWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = PixelWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            float mCenterX = 320 / 2f;
            float mCenterY = 320 / 2f;


            //Draw animation

            Matrix rotator = new Matrix();

            float tickRot5 = (float) ((mTime.second+2) * Math.PI * 2 / 60);
            float innerTickRadius10 = mCenterX - 30;
            float innerX5 = (float) Math.sin(tickRot5) * innerTickRadius10;
            float innerY5 = (float) -Math.cos(tickRot5) * innerTickRadius10;

            rotator.setTranslate(mCenterX + innerX5, mCenterY + innerY5);
            rotator.postRotate((float) Math.toDegrees((float) ((mTime.second + 1) * Math.PI * 2 / 60)) - 180, mCenterX + innerX5, mCenterY + innerY5);
            canvas.drawBitmap(bmpsecond[mTime.second % 4], rotator, mBackgroundPaint);





            if(mTime.second>58){
                rotator.reset();
                tickRot5 = (float) (((mTime.minute+3) * Math.PI * 2 / 60)+Math.PI * 2 / 120);
                innerTickRadius10 = mCenterX - 70;
                innerX5 = (float) Math.sin(tickRot5) * innerTickRadius10;
                innerY5 = (float) -Math.cos(tickRot5) * innerTickRadius10;
                rotator.setTranslate(mCenterX + innerX5, mCenterY + innerY5);
                rotator.postRotate((float)Math.toDegrees((float)((mTime.minute) * Math.PI * 2 / 60))-180, mCenterX + innerX5, mCenterY + innerY5);
                canvas.drawBitmap(bmpminute[0], rotator, mBackgroundPaint);
            }else{
                rotator.reset();

                tickRot5 = (float) ((mTime.minute+2) * Math.PI * 2 / 60);
                innerTickRadius10 = mCenterX - 70;
                innerX5 = (float) Math.sin(tickRot5) * innerTickRadius10;
                innerY5 = (float) -Math.cos(tickRot5) * innerTickRadius10;
                rotator.setTranslate(mCenterX + innerX5, mCenterY + innerY5);
                rotator.postRotate((float) Math.toDegrees((float) ((mTime.minute) * Math.PI * 2 / 60)) - 180, mCenterX + innerX5, mCenterY + innerY5);
                canvas.drawBitmap(bmpminute[1], rotator, mBackgroundPaint);
            }

            if((mTime.minute>59)&&(mTime.second>55)){
                rotator.reset();
                tickRot5 = (float) (((mTime.hour+0.3) * Math.PI * 2 / 12)+(Math.PI * 2 / 60)*mTime.second%5);
                innerTickRadius10 = mCenterX - 70;
                innerX5 = (float) Math.sin(tickRot5) * innerTickRadius10;
                innerY5 = (float) -Math.cos(tickRot5) * innerTickRadius10;
                rotator.setTranslate(mCenterX + innerX5, mCenterY + innerY5);
                rotator.postRotate((float)Math.toDegrees((float)((mTime.hour) * Math.PI * 2 / 12))-180, mCenterX + innerX5, mCenterY + innerY5);
                canvas.drawBitmap(bmphour[mTime.second%2], rotator, mBackgroundPaint);
            }else{
                rotator.reset();

                tickRot5 = (float) ((mTime.hour+0.3) * Math.PI * 2 / 12);
                innerTickRadius10 = mCenterX - 70;
                innerX5 = (float) Math.sin(tickRot5) * innerTickRadius10;
                innerY5 = (float) -Math.cos(tickRot5) * innerTickRadius10;
                rotator.setTranslate(mCenterX + innerX5, mCenterY + innerY5);
                rotator.postRotate((float) Math.toDegrees((float) ((mTime.hour) * Math.PI * 2 / 12)) - 180, mCenterX + innerX5, mCenterY + innerY5);
                canvas.drawBitmap(bmphour[1], rotator, mBackgroundPaint);
            }



            //canvas.drawLine(mCenterX + innerX5, mCenterY + innerY5,
            //        mCenterX + innerX5+1, mCenterY + innerY5+1, mTickAndCirclePaint1);
/*
            mTickAndCirclePaint1.setColor(getResources().getColor(R.color.green));
            float tickRot2 = (float) ((mTime.hour * Math.PI * 2 / 12)+mTime.minute*(Math.PI * 2 / (12*60)));
            float innerTickRadius2 = mCenterX - 20;
            float outerTickRadius2 = mCenterX;
            float innerX2 = (float) Math.sin(tickRot2) * innerTickRadius2;
            float innerY2 = (float) -Math.cos(tickRot2) * innerTickRadius2;
            float outerX2 = (float) Math.sin(tickRot2) * outerTickRadius2;
            float outerY2 = (float) -Math.cos(tickRot2) * outerTickRadius2;
            canvas.drawLine(mCenterX + innerX2, mCenterY + innerY2,
                    mCenterX + outerX2, mCenterY + outerY2, mTickAndCirclePaint1);
*

            /*mTickAndCirclePaint1.setColor(getResources().getColor(R.color.blue));
            tickRot2 = (float) ((mTime.minute * Math.PI * 2 / 60)+mTime.second*(Math.PI * 2 / (60*60)));
             innerTickRadius2 = mCenterX - 40;
             outerTickRadius2 = mCenterX;
             innerX2 = (float) Math.sin(tickRot2) * innerTickRadius2;
             innerY2 = (float) -Math.cos(tickRot2) * innerTickRadius2;
             outerX2 = (float) Math.sin(tickRot2) * outerTickRadius2;
             outerY2 = (float) -Math.cos(tickRot2) * outerTickRadius2;
            canvas.drawLine(mCenterX + innerX2, mCenterY + innerY2,
                    mCenterX + outerX2, mCenterY + outerY2, mTickAndCirclePaint1);*/

            if(!mAmbient) {
                /*mTickAndCirclePaint1.setColor(getResources().getColor(R.color.red));
                float tickRot1 = (float) ((mTime.second+1) * Math.PI * 2 / 60);
                float innerTickRadius1 = mCenterX - 15;
                float outerTickRadius1 = mCenterX;
                float innerX1 = (float) Math.sin(tickRot1) * innerTickRadius1;
                float innerY1 = (float) -Math.cos(tickRot1) * innerTickRadius1;
                float outerX1 = (float) Math.sin(tickRot1) * outerTickRadius1;
                float outerY1 = (float) -Math.cos(tickRot1) * outerTickRadius1;
                canvas.drawLine(mCenterX + innerX1, mCenterY + innerY1,
                        mCenterX + outerX1, mCenterY + outerY1, mTickAndCirclePaint1);
                */
                for (int tickIndex = 0; tickIndex < 60; tickIndex++) {
                    float innerTickRadius;
                    if(tickIndex%5 == 0){
                        innerTickRadius = mCenterX - 9;
                    }else{
                        innerTickRadius = mCenterX - 2;
                    }
                    float tickRot = (float) (tickIndex * Math.PI * 2 / 60);

                    float outerTickRadius = mCenterX;
                    float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                    float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                    float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                    float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                    canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                            mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint);


                }
            }

            final Rect textBounds = new Rect();


            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String text = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d", mTime.hour, mTime.minute, mTime.second);


            mTextPaint.getTextBounds(text, 0, text.length(), textBounds);

            canvas.drawText(text, mCenterX - textBounds.exactCenterX(), mCenterY - textBounds.exactCenterY(), mTextPaint);

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<PixelWatchFaceService.Engine> mWeakReference;

        public EngineHandler(PixelWatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            PixelWatchFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
