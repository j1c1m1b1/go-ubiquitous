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

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    private static final String WEAR_PATH = "/weather_info/wear";

    private static final String LOW_TEMP = "low";
    private static final String HIGH_TEMP = "high";
    private static final String IMAGE = "image";

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    private static final int MSG_LOAD_WEATHER = 1;

    private static final long DELAY = 1800000;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private static class LoadWeatherHandler extends Handler
    {
        private final WeakReference<SunshineWatchFace.Engine> weakReference;

        public LoadWeatherHandler(SunshineWatchFace.Engine reference)
        {
            weakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = weakReference.get();
            if(engine != null)
            {
                switch (msg.what)
                {
                    case MSG_LOAD_WEATHER:
                        engine.handleLoadWeather();
                        break;
                }
            }
        }


    }

    @SuppressWarnings("deprecation")
    private class Engine extends CanvasWatchFaceService.Engine
            implements GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener
    {


        final Handler mUpdateTimeHandler = new EngineHandler(this);
        final Handler loadWeatherHandler = new LoadWeatherHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mDateTextPaint;
        Paint mBitmapPaint;
        Paint mHighTextPaint;
        Paint mLowTextPaint;
        boolean mAmbient;
        Time mTime;
        float mXOffset;
        float mYOffset;
        float dateYOffset;
        float weatherYOffset;
        float bitmapXOffset;
        float bitmapYOffset;
        float highXOffset;
        float lowXOffset;

        int bitmapSize;

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        private DateFormat dateFormat;
        private Date date;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
                date.setTime(System.currentTimeMillis());
            }
        };
        private int low;
        private int high;
        private byte[] bitmapBytes;

        @Override
        public void onConnected(Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            Log.i(this.getClass().getSimpleName(), "Connected");

        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.i(this.getClass().getSimpleName(), "Connection suspended: " + i);
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.e(this.getClass().getSimpleName(), "Connection failed: "
                    + connectionResult.getErrorMessage());
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for(DataEvent dataEvent : dataEventBuffer)
            {
                if(dataEvent.getType() == DataEvent.TYPE_CHANGED)
                {
                    DataMap dataMap =
                            DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                    String path = dataEvent.getDataItem().getUri().getPath();
                    if(path.equals(WEAR_PATH))
                    {
                        low = (int) dataMap.getDouble(LOW_TEMP);
                        high = (int) dataMap.getDouble(HIGH_TEMP);
                        bitmapBytes = dataMap.getByteArray(IMAGE);

                        break;
                    }
                }
            }
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            dateYOffset = resources.getDimension(R.dimen.date_y_offset);
            weatherYOffset = resources.getDimension(R.dimen.weather_y_offset);
            bitmapXOffset = resources.getDimension(R.dimen.bitmap_x_offset);
            bitmapYOffset = resources.getDimension(R.dimen.bitmap_y_offset);
            highXOffset = resources.getDimension(R.dimen.high_x_offset);
            lowXOffset = resources.getDimension(R.dimen.low_x_offset);
            bitmapSize = resources.getDimensionPixelSize(R.dimen.bitmap_size);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(getResources().getColor(R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(getResources().getColor(R.color.digital_text));
            mTextPaint.setTextAlign(Paint.Align.CENTER);

            mDateTextPaint = createTextPaint(getResources().getColor(R.color.digital_text));
            mDateTextPaint.setTextAlign(Paint.Align.CENTER);
            mDateTextPaint.setTextSize(getResources().getDimension(R.dimen.date_text_size));

            mHighTextPaint = createBoldTextPaint(getResources().getColor(R.color.digital_text));
            mHighTextPaint.setTextSize(getResources().getDimension(R.dimen.weather_text_size));

            mLowTextPaint = createTextPaint(getResources().getColor(R.color.digital_text));
            mLowTextPaint.setTextSize(getResources().getDimension(R.dimen.weather_text_size));

            mBitmapPaint = createBitmapPaint();

            mTime = new Time();

            date = new Date();

            dateFormat = new SimpleDateFormat("ccc, MMM dd yyyy", Locale.US);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            loadWeatherHandler.removeMessages(MSG_LOAD_WEATHER);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        private Paint createBoldTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(BOLD_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        private Paint createBitmapPaint()
        {
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setDither(true);
            paint.setFilterBitmap(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
                date.setTime(System.currentTimeMillis());
                loadWeatherHandler.sendEmptyMessage(MSG_LOAD_WEATHER);
            } else {

                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
                loadWeatherHandler.removeMessages(MSG_LOAD_WEATHER);
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
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);

            float dateTextSize = resources.getDimension(R.dimen.date_text_size);

            mDateTextPaint.setTextSize(dateTextSize);
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
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String text = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);
            canvas.drawText(text, bounds.centerX(), mYOffset, mTextPaint);

            if(!mAmbient)
            {
                String dateText = dateFormat.format(date);
                canvas.drawText(dateText, bounds.centerX(),dateYOffset, mDateTextPaint);

                if(bitmapBytes != null)
                {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0,
                            bitmapBytes.length);

                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, bitmapSize,
                            bitmapSize, false);

                    canvas.drawBitmap(scaledBitmap, bitmapXOffset, bitmapYOffset, mBitmapPaint);

                    canvas.drawText(high + "º", highXOffset, weatherYOffset, mHighTextPaint);

                    canvas.drawText(low + "º", lowXOffset, weatherYOffset, mLowTextPaint);
                }
            }

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

        public void handleLoadWeather() {
            Intent intent = new Intent(getApplicationContext(), LoadWeatherService.class);
            startService(intent);
            loadWeatherHandler.sendEmptyMessageDelayed(MSG_LOAD_WEATHER, DELAY);
        }
    }
}
