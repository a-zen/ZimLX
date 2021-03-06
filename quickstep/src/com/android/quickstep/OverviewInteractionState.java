/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;

import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.DiscoveryBounce;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.UiThreadHelper;
import com.android.systemui.shared.recents.ISystemUiProxy;

import androidx.annotation.WorkerThread;

import org.jetbrains.annotations.NotNull;
import org.zimmob.zimlx.ZimPreferences;

import android.provider.Settings;

/**
 * Sets alpha for the back button
 */
public class OverviewInteractionState implements ZimPreferences.OnPreferenceChangeListener {

    private static final String TAG = "OverviewFlags";

    private static final String HAS_ENABLED_QUICKSTEP_ONCE = "launcher.has_enabled_quickstep_once";
    public static final String SWIPE_UP_SETTING_NAME = "";
    private static final String SWIPE_UP_SETTING_AVAILABLE_RES_NAME =
            "config_swipe_up_gesture_setting_available";
    private static final String CUSTOM_SWIPE_UP_SETTING_AVAILABLE_RES_NAME =
            "config_custom_swipe_up_gesture_setting_available";
    private static final String SWIPE_UP_ENABLED_DEFAULT_RES_NAME =
            "config_swipe_up_gesture_default";

    // We do not need any synchronization for this variable as its only written on UI thread.
    public static final MainThreadInitializedObject<OverviewInteractionState> INSTANCE =
            new MainThreadInitializedObject<>(OverviewInteractionState::new);

    private static final int MSG_SET_PROXY = 200;
    private static final int MSG_SET_BACK_BUTTON_ALPHA = 201;
    private static final int MSG_SET_SWIPE_UP_ENABLED = 202;

    private final Context mContext;
    private final Handler mUiHandler;
    private final Handler mBgHandler;

    // These are updated on the background thread
    private ISystemUiProxy mISystemUiProxy;
    private float mBackButtonAlpha = 1;

    private int mSystemUiStateFlags;
    private final SwipeUpGestureEnabledSettingObserver mSwipeUpSettingObserver;
    private boolean mSwipeUpEnabled = false;
    private Runnable mOnSwipeUpSettingChangedListener;

    private OverviewInteractionState(Context context) {
        mContext = context;

        // Data posted to the uihandler will be sent to the bghandler. Data is sent to uihandler
        // because of its high send frequency and data may be very different than the previous value
        // For example, send back alpha on uihandler to avoid flickering when setting its visibility
        mUiHandler = new Handler(this::handleUiMessage);
        mBgHandler = new Handler(UiThreadHelper.getBackgroundLooper(), this::handleBgMessage);

        onNavigationModeChanged(SysUINavigationMode.INSTANCE.get(context)
                .addModeChangeListener(this::onNavigationModeChanged));

        if (isSwipeUpSettingsAvailable()) {
            mSwipeUpSettingObserver = new SwipeUpGestureEnabledSettingObserver(mUiHandler, context.getContentResolver());
            mSwipeUpSettingObserver.register();
        } else {
            mSwipeUpSettingObserver = null;
            Utilities.getZimPrefs(context).addOnPreferenceChangeListener("pref_swipe_up_to_switch_apps_enabled", this);
            mSwipeUpEnabled = getSystemBooleanRes(SWIPE_UP_ENABLED_DEFAULT_RES_NAME);
        }
    }

    public static boolean isSwipeUpSettingsAvailable() {
        return getSystemBooleanRes(CUSTOM_SWIPE_UP_SETTING_AVAILABLE_RES_NAME,
                SWIPE_UP_SETTING_AVAILABLE_RES_NAME);
    }

    public float getBackButtonAlpha() {
        return mBackButtonAlpha;
    }

    private static boolean getSystemBooleanRes(String resName, String fallback) {
        Resources res = Resources.getSystem();
        int resId = res.getIdentifier(resName, "bool", "android");

        if (resId != 0) {
            return res.getBoolean(resId);
        } else {
            return getSystemBooleanRes(fallback);
        }
    }

    public void setSystemUiProxy(ISystemUiProxy proxy) {
        mBgHandler.obtainMessage(MSG_SET_PROXY, proxy).sendToTarget();
    }

    public void setSystemUiStateFlags(int stateFlags) {
        mSystemUiStateFlags = stateFlags;
    }

    public int getSystemUiStateFlags() {
        return mSystemUiStateFlags;
    }

    private boolean handleUiMessage(Message msg) {
        if (msg.what == MSG_SET_BACK_BUTTON_ALPHA) {
            mBackButtonAlpha = (float) msg.obj;
        }
        mBgHandler.obtainMessage(msg.what, msg.arg1, msg.arg2, msg.obj).sendToTarget();
        return true;
    }

    private static boolean getSystemBooleanRes(String resName) {
        Resources res = Resources.getSystem();
        int resId = res.getIdentifier(resName, "bool", "android");

        if (resId != 0) {
            return res.getBoolean(resId);
        } else {
            Log.e(TAG, "Failed to get system resource ID. Incompatible framework version?");
            return false;
        }
    }

    @WorkerThread
    private void applyBackButtonAlpha(float alpha, boolean animate) {
        if (mISystemUiProxy == null) {
            return;
        }
        try {
            mISystemUiProxy.setBackButtonAlpha(alpha, animate);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to update overview back button alpha", e);
        }
    }

    private void onNavigationModeChanged(SysUINavigationMode.Mode mode) {
        resetHomeBounceSeenOnQuickstepEnabledFirstTime();
    }

    private boolean modeSupportsGestures() {
        if (SysUINavigationMode.INSTANCE.get(mContext).getMode() != null) {
            return SysUINavigationMode.getMode(mContext).hasGestures;
        } else {
            return false;
        }
    }

    public void setOnSwipeUpSettingChangedListener(Runnable listener) {
        mOnSwipeUpSettingChangedListener = listener;
    }

    private class SwipeUpGestureEnabledSettingObserver extends ContentObserver {
        private final int defaultValue;
        private Handler mHandler;
        private ContentResolver mResolver;

        SwipeUpGestureEnabledSettingObserver(Handler handler, ContentResolver resolver) {
            super(handler);
            mHandler = handler;
            mResolver = resolver;
            defaultValue = getSystemBooleanRes(SWIPE_UP_ENABLED_DEFAULT_RES_NAME) ? 1 : 0;
        }

        public void register() {
            mResolver.registerContentObserver(Settings.Secure.getUriFor(SWIPE_UP_SETTING_NAME),
                    false, this);
            mSwipeUpEnabled = getValue();
            resetHomeBounceSeenOnQuickstepEnabledFirstTime();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            mHandler.removeMessages(MSG_SET_SWIPE_UP_ENABLED);
            mHandler.obtainMessage(MSG_SET_SWIPE_UP_ENABLED, getValue() ? 1 : 0, 0).sendToTarget();
        }

        private boolean getValue() {
            return Settings.Secure.getInt(mResolver, SWIPE_UP_SETTING_NAME, defaultValue) == 1;
        }
    }

    public boolean isSwipeUpGestureEnabled() {
        return mSwipeUpEnabled;
    }

    public void setBackButtonAlpha(float alpha, boolean animate) {
        if (!mSwipeUpEnabled) {
            alpha = 1;
        } else if (Utilities.getZimPrefs(mContext).getSwipeLeftToGoBack()) {
            alpha = 0;
        }
        mUiHandler.removeMessages(MSG_SET_BACK_BUTTON_ALPHA);
        mUiHandler.obtainMessage(MSG_SET_BACK_BUTTON_ALPHA, animate ? 1 : 0, 0, alpha)
                .sendToTarget();
    }

    private boolean handleBgMessage(Message msg) {
        switch (msg.what) {
            case MSG_SET_PROXY:
                mISystemUiProxy = (ISystemUiProxy) msg.obj;
                break;
            case MSG_SET_BACK_BUTTON_ALPHA:
                applyBackButtonAlpha((float) msg.obj, msg.arg1 == 1);
                return true;
            case MSG_SET_SWIPE_UP_ENABLED:
                mSwipeUpEnabled = msg.arg1 != 0;
                resetHomeBounceSeenOnQuickstepEnabledFirstTime();

                if (mOnSwipeUpSettingChangedListener != null) {
                    mOnSwipeUpSettingChangedListener.run();
                }
                break;

        }
        return true;
    }

    private void resetHomeBounceSeenOnQuickstepEnabledFirstTime() {
        if (mSwipeUpEnabled && !Utilities.getPrefs(mContext).getBoolean(
                HAS_ENABLED_QUICKSTEP_ONCE, true)) {
            Utilities.getPrefs(mContext).edit()
                    .putBoolean(HAS_ENABLED_QUICKSTEP_ONCE, true)
                    .putBoolean(DiscoveryBounce.HOME_BOUNCE_SEEN, false)
                    .apply();
        }
    }

    @Override
    public void onValueChanged(@NotNull String key, @NotNull ZimPreferences prefs, boolean force) {
        mBgHandler.removeMessages(MSG_SET_SWIPE_UP_ENABLED);
        mBgHandler.obtainMessage(MSG_SET_SWIPE_UP_ENABLED, prefs.getSwipeUpToSwitchApps() ? 1 : 0, 0).sendToTarget();
    }
}
