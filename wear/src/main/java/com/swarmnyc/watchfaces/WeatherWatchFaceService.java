package com.swarmnyc.watchfaces;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.TimeZone;

public class WeatherWatchFaceService extends WeatherWatchFaceServiceBase {
    @Override
    public Engine onCreateEngine() {
        return new Engine(this);
    }

    private class Engine extends WeatherWatchFaceEngine {
        public Engine(WatchFaceService service) {
            super(service);
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            mBackgroundColor = mBackgroundDefaultColor = mResources.getColor(R.color.weather_bg_color);
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(mBackgroundDefaultColor);

            mTemperatureBorderPaint = new Paint();
            mTemperatureBorderPaint.setStyle(Paint.Style.STROKE);
            mTemperatureBorderPaint.setColor(mResources.getColor(R.color.weather_temperature_border_color));
            mTemperatureBorderPaint.setStrokeWidth(3f);
            mTemperatureBorderPaint.setAntiAlias(true);

            Typeface timeFont = Typeface.createFromAsset(mAsserts, mResources.getString(R.string.weather_time_font));
            Typeface dateFont = Typeface.createFromAsset(mAsserts, mResources.getString(R.string.weather_date_font));
            Typeface tempFont = Typeface.createFromAsset(mAsserts, mResources.getString(R.string.weather_temperature_font));

            mTimePaint = createTextPaint(mResources.getColor(R.color.weather_time_color), timeFont);
            mDatePaint = createTextPaint(mResources.getColor(R.color.weather_date_color), dateFont);
            mDateSuffixPaint = createTextPaint(mResources.getColor(R.color.weather_date_color), dateFont);
            mTemperaturePaint = createTextPaint(mResources.getColor(R.color.weather_temperature_color), tempFont);
            mTemperatureSuffixPaint = createTextPaint(mResources.getColor(R.color.weather_temperature_color), tempFont);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            isRound = insets.isRound();

            mInternalDistance = mResources.getDimension(isRound ?
                    R.dimen.weather_internal_distance_round : R.dimen.weather_internal_distance);

            mTimeXOffset = mResources.getInteger(isRound ?
                    R.integer.weather_time_xoffset_round : R.integer.weather_time_xoffset);

            mTimeYOffset = mResources.getInteger(isRound ?
                    R.integer.weather_time_yoffset_round : R.integer.weather_time_yoffset);

            float timeTextSize = mResources.getDimension(isRound ?
                    R.dimen.weather_time_size_round : R.dimen.weather_time_size);

            float dateTextSize = mResources.getDimension(isRound ?
                    R.dimen.weather_date_size_round : R.dimen.weather_date_size);

            float dateSuffixTextSize = mResources.getDimension(isRound ?
                    R.dimen.weather_date_suffix_size_round : R.dimen.weather_date_suffix_size);

            float tempTextSize = mResources.getDimension(isRound ?
                    R.dimen.weather_temperature_size_round : R.dimen.weather_temperature_size);

            float tempSuffixTextSize = mResources.getDimension(isRound ?
                    R.dimen.weather_temperature_suffix_size_round : R.dimen.weather_temperature_suffix_size);

            mTimePaint.setTextSize(timeTextSize);
            mDatePaint.setTextSize(dateTextSize);
            mDateSuffixPaint.setTextSize(dateSuffixTextSize);
            mTemperaturePaint.setTextSize(tempTextSize);
            mTemperatureSuffixPaint.setTextSize(tempSuffixTextSize);

            mTimeYOffset += (mTimePaint.descent() + mTimePaint.ascent()) / 2;
            mColonXOffset = mTimePaint.measureText(Consts.COLON_STRING) / 2;
            mDateYOffset = (mDatePaint.descent() + mDatePaint.ascent()) / 2;
            mDateSuffixYOffset = (mDateSuffixPaint.descent() + mDateSuffixPaint.ascent()) / 2;
            mTemperatureYOffset = (mTemperaturePaint.descent() + mTemperaturePaint.ascent()) / 2;
            mTemperatureSuffixYOffset = (mTemperatureSuffixPaint.descent() + mTemperatureSuffixPaint.ascent()) / 2;
            mDebugInfoYOffset = 5 + mDebugInfoPaint.getTextSize() + (mDebugInfoPaint.descent() + mDebugInfoPaint.ascent()) / 2;
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            //log("Draw");
            mTime.setToNow();

            boolean hasPeekCard = getPeekCardPosition().top != 0;
            int width = bounds.width();
            int height = bounds.height();
            float radius = width / 2;
            float yOffset;
            if (hasPeekCard) {
                yOffset = height * 0.05f;
            } else {
                yOffset = 0;
            }

            canvas.drawRect(0, 0, width, height, mBackgroundPaint);

            // Time
            String hourString = String.format("%02d", ConverterUtil.convertHour(mTime.hour, mTimeUnit));
            String minString = String.format("%02d", mTime.minute);

            //For Test
//            hourString = "11";
//            minString = "34";
//            mTemperature = 50;
//            mWeatherCondition = "clear";
//            mWeatherInfoReceivedTime = System.currentTimeMillis();
//            mSunriseTime.set(mWeatherInfoReceivedTime-10000);
//            mSunsetTime.set(mWeatherInfoReceivedTime+10000);

            float hourWidth = mTimePaint.measureText(hourString);

            float x = radius - hourWidth - mColonXOffset + mTimeXOffset;
            float y = radius - mTimeYOffset - yOffset;
            float suffixY;

            canvas.drawText(hourString, x, y, mTimePaint);

            x = radius - mColonXOffset + mTimeXOffset;
            canvas.drawText(Consts.COLON_STRING, x, y, mTimePaint);

            x = radius + mColonXOffset + mTimeXOffset;
            canvas.drawText(minString, x, y, mTimePaint);

            suffixY = y + mDateSuffixPaint.getTextSize() +
                    mInternalDistance +
                    mDateSuffixYOffset;

            y += mDatePaint.getTextSize() + mInternalDistance + mDateYOffset;

            //Date
            String monthString = ConverterUtil.convertToMonth(mTime.month);
            String dayString = String.valueOf(mTime.monthDay);
            String daySuffixString = ConverterUtil.getDaySuffix(mTime.monthDay);

            float monthWidth = mDatePaint.measureText(monthString);
            float dayWidth = mDatePaint.measureText(dayString);
            float dateWidth = monthWidth + dayWidth +
                    mDateSuffixPaint.measureText(daySuffixString);

            x = radius - dateWidth / 2;
            canvas.drawText(monthString, x, y, mDatePaint);
            x += monthWidth;
            canvas.drawText(dayString, x, y, mDatePaint);
            x += dayWidth;
            canvas.drawText(daySuffixString, x, suffixY, mDateSuffixPaint);

            //WeatherInfo
            long timeSpan = System.currentTimeMillis() - mWeatherInfoReceivedTime;
            if (timeSpan <= WEATHER_INFO_TIME_OUT) {
                // photo
                if (!TextUtils.isEmpty(mWeatherCondition)) {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("weather_");
                    stringBuilder.append(mWeatherCondition);

                    if ((mWeatherCondition.equals("cloudy") || mWeatherCondition.equals("clear")) && (Time.compare(mTime, mSunriseTime) < 0 || Time.compare(mTime, mSunsetTime) > 0)) {
                        //cloudy and clear have night picture
                        stringBuilder.append("night");
                    }
                    if (this.isInAmbientMode()) {
                        stringBuilder.append("_gray");
                    }

                    String name = stringBuilder.toString();
                    if (!name.equals(mWeatherConditionResourceName)) {
                        log("CreateScaledBitmap: " + name);
                        mWeatherConditionResourceName = name;
                        int id = mResources.getIdentifier(name, "drawable", this.getClass().getPackage().getName());

                        Drawable b = mResources.getDrawable(id);
                        mWeatherConditionDrawable = ((BitmapDrawable) b).getBitmap();
                        float sizeScale = (width * 0.5f) / mWeatherConditionDrawable.getWidth();
                        mWeatherConditionDrawable = Bitmap.createScaledBitmap(mWeatherConditionDrawable, (int) (mWeatherConditionDrawable.getWidth() * sizeScale), (int) (mWeatherConditionDrawable.getHeight() * sizeScale), true);
                    }

                    canvas.drawBitmap(mWeatherConditionDrawable, radius - mWeatherConditionDrawable.getWidth() / 2, 0 - yOffset, null);
                }

                //temperature
                if (mTemperature != Integer.MAX_VALUE && !(isRound && hasPeekCard)) {
                    String temperatureString = String.valueOf(mTemperature);
                    String temperatureScaleString = mTemperatureScale == ConverterUtil.FAHRENHEIT ? ConverterUtil.FAHRENHEIT_STRING : ConverterUtil.CELSIUS_STRING;
                    float temperatureWidth = mTemperaturePaint.measureText(temperatureString);
                    float temperatureRadius = (temperatureWidth + mTemperatureSuffixPaint.measureText(temperatureScaleString)) / 2;
                    float borderPadding = temperatureRadius * 0.5f;
                    x = radius;
                    if (hasPeekCard) {
                        y = getPeekCardPosition().top - temperatureRadius - borderPadding - mTemperatureBorderPaint.getStrokeWidth() * 2;
                    } else {
                        y = bounds.height() * 0.80f;
                    }

                    suffixY = y - mTemperatureSuffixYOffset;
                    canvas.drawCircle(radius, y + borderPadding / 2, temperatureRadius + borderPadding, mTemperatureBorderPaint);

                    x -= temperatureRadius;
                    y -= mTemperatureYOffset;
                    canvas.drawText(temperatureString, x, y, mTemperaturePaint);
                    x += temperatureWidth;
                    canvas.drawText(temperatureScaleString, x, suffixY, mTemperatureSuffixPaint);
                }
            }


            if (BuildConfig.DEBUG) {
                String timeString;
                if (mWeatherInfoReceivedTime == 0) {
                    timeString = "No data received";
                } else if (timeSpan > DateUtils.HOUR_IN_MILLIS) {
                    timeString = "Get: " + String.valueOf(timeSpan / DateUtils.HOUR_IN_MILLIS) + " hours ago";
                } else if (timeSpan > DateUtils.MINUTE_IN_MILLIS) {
                    timeString = "Get: " + String.valueOf(timeSpan / DateUtils.MINUTE_IN_MILLIS) + " mins ago";
                } else {
                    timeString = "Get: " + String.valueOf(timeSpan / DateUtils.SECOND_IN_MILLIS) + " secs ago";
                }

                canvas.drawText(timeString, width - mDebugInfoPaint.measureText(timeString), mDebugInfoYOffset, mDebugInfoPaint);
            }
        }
    }
}
