/*
 * 2020 Zim Launcher
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
package org.zimmob.zimlx.icons.clock;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.os.Process;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.RequiresApi;

import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.util.Preconditions;

import java.util.Collections;
import java.util.Set;
import java.util.TimeZone;
import java.util.WeakHashMap;

public class DynamicClock extends BroadcastReceiver {
    public static final ComponentName DESK_CLOCK = new ComponentName(
            "com.google.android.deskclock",
            "com.android.deskclock.DeskClock");

    private final Set<AutoUpdateClock> mUpdaters;
    private final Context mContext;
    private ClockLayers mLayers;

    public DynamicClock(Context context) {
        mUpdaters = Collections.newSetFromMap(new WeakHashMap<>());
        mLayers = new ClockLayers();
        mContext = context;
        final Handler handler = new Handler(LauncherModel.getWorkerLooper());

        IntentFilter filter = new IntentFilter();
        filter.addDataScheme("package");
        filter.addDataSchemeSpecificPart(DESK_CLOCK.getPackageName(), 0);
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);

        mContext.registerReceiver(this, filter, null, handler);
        handler.post(this::updateMainThread);

        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                loadTimeZone(intent.getStringExtra("time-zone"));
            }
        }, new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED), null, new Handler(Looper.getMainLooper()));
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static Drawable getClock(Context context, int iconDpi) {
        ClockLayers clone = getClockLayers(context, iconDpi, false).clone();
        if (clone != null) {
            clone.updateAngles();
            return clone.mDrawable;
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static ClockLayers getClockLayers(Context context, int iconDpi, boolean normalizeIcon) {
        Preconditions.assertWorkerThread();
        ClockLayers layers = new ClockLayers();
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo("com.google.android.deskclock", PackageManager.GET_META_DATA | PackageManager.GET_UNINSTALLED_PACKAGES);
            Bundle metaData = applicationInfo.metaData;
            if (metaData != null) {
                int levelPerTickIcon = metaData.getInt("com.google.android.apps.nexuslauncher.LEVEL_PER_TICK_ICON_ROUND", 0);
                if (levelPerTickIcon != 0) {
                    Drawable drawableForDensity = packageManager.getResourcesForApplication(applicationInfo).getDrawableForDensity(levelPerTickIcon, iconDpi);
                    layers.setDrawable(drawableForDensity.mutate());
                    layers.mHourIndex = metaData.getInt("com.google.android.apps.nexuslauncher.HOUR_LAYER_INDEX", -1);
                    layers.mMinuteIndex = metaData.getInt("com.google.android.apps.nexuslauncher.MINUTE_LAYER_INDEX", -1);
                    layers.mSecondIndex = metaData.getInt("com.google.android.apps.nexuslauncher.SECOND_LAYER_INDEX", -1);
                    layers.mDefaultHour = metaData.getInt("com.google.android.apps.nexuslauncher.DEFAULT_HOUR", 0);
                    layers.mDefaultMinute = metaData.getInt("com.google.android.apps.nexuslauncher.DEFAULT_MINUTE", 0);
                    layers.mDefaultSecond = metaData.getInt("com.google.android.apps.nexuslauncher.DEFAULT_SECOND", 0);
                    if (normalizeIcon) {
                        LauncherIcons obtain = LauncherIcons.obtain(context);
                        float[] scale = new float[1];
                        layers.bitmap = obtain.createBadgedIconBitmap(
                                new AdaptiveIconDrawable(layers.mDrawable.getBackground().getConstantState().newDrawable(), null),
                                Process.myUserHandle(), Build.VERSION_CODES.O, false, scale).icon;
                        layers.scale = scale[0];
                        int iconBitmapSize = LauncherAppState.getInstance(context).getInvariantDeviceProfile().iconBitmapSize;
                        layers.offset = (int) Math.ceil(0.010416667f * ((float) iconBitmapSize));
                        obtain.recycle();
                    }

                    LayerDrawable layerDrawable = layers.mLayerDrawable;
                    int numberOfLayers = layerDrawable.getNumberOfLayers();

                    if (layers.mHourIndex < 0 || layers.mHourIndex >= numberOfLayers) {
                        layers.mHourIndex = -1;
                    }
                    if (layers.mMinuteIndex < 0 || layers.mMinuteIndex >= numberOfLayers) {
                        layers.mMinuteIndex = -1;
                    }
                    if (layers.mSecondIndex < 0 || layers.mSecondIndex >= numberOfLayers) {
                        layers.mSecondIndex = -1;
                    } else {
                        layerDrawable.setDrawable(layers.mSecondIndex, null);
                        layers.mSecondIndex = -1;
                    }
                }
            }
        } catch (Exception e) {
            layers.mDrawable = null;
        }
        return layers;
    }

    private void loadTimeZone(String timeZoneId) {
        TimeZone timeZone = timeZoneId == null ?
                TimeZone.getDefault() :
                TimeZone.getTimeZone(timeZoneId);

        for (AutoUpdateClock a : mUpdaters) {
            a.setTimeZone(timeZone);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void updateMainThread() {
        new MainThreadExecutor().execute(() -> updateWrapper(getClockLayers(mContext,
                LauncherAppState.getIDP(mContext).fillResIconDpi,
                true)));
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void updateWrapper(ClockLayers wrapper) {
        this.mLayers = wrapper;
        for (AutoUpdateClock updater : mUpdaters) {
            updater.updateLayers(wrapper.clone());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public AutoUpdateClock drawIcon(ItemInfoWithIcon info) {
        final AutoUpdateClock updater = new AutoUpdateClock(info, mLayers.clone());
        mUpdaters.add(updater);
        return updater;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onReceive(Context context, Intent intent) {
        updateMainThread();
    }
}
