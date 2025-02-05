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

package com.android.systemui.qs.tiles;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiDevice;
import android.provider.Settings;
import android.widget.Toast;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.Prefs;
import com.android.systemui.R;
import com.android.systemui.SysUIToast;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.UsageTracker;
import com.android.systemui.statusbar.policy.HotspotController;

import java.util.List;

/** Quick settings tile: Hotspot **/
public class HotspotTile extends QSTile<QSTile.BooleanState> {
    private static final Intent TETHER_SETTINGS = new Intent().setComponent(new ComponentName(
            "com.android.settings", "com.android.settings.TetherSettings"));

    private final AnimationIcon mEnable = new AnimationIcon(R.drawable.ic_hotspot_enable_animation);
    private final AnimationIcon mDisable = new AnimationIcon(R.drawable.ic_hotspot_disable_animation);
    private final HotspotController mController;
    private final Callback mCallback = new Callback();
    private final UsageTracker mUsageTracker;
    private final ConnectivityManager mConnectivityManager;
    private boolean mListening;

    public HotspotTile(Host host) {
        super(host);
        mController = host.getHotspotController();
        mUsageTracker = newUsageTracker(host.getContext());
        mUsageTracker.setListening(true);
        mConnectivityManager = host.getContext().getSystemService(ConnectivityManager.class);
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        mUsageTracker.setListening(false);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        if (listening) {
            mController.addCallback(mCallback);
            mContext.registerReceiver(mTetherConnectStateChangedReceiver,
                    new IntentFilter(ConnectivityManager.TETHER_CONNECT_STATE_CHANGED));
        } else {
            mController.removeCallback(mCallback);
            mContext.unregisterReceiver(mTetherConnectStateChangedReceiver);
        }
        mListening = listening;
    }

    @Override
    protected void handleClick() {
        boolean airplaneMode = (Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) == 1);
        if (airplaneMode) {
            mHost.collapsePanels();
            SysUIToast.makeText(mContext, mContext.getString(
            R.string.toast_airplane_mode_hotspot),
            Toast.LENGTH_LONG).show();
        } else {
        final boolean isEnabled = (Boolean) mState.value;
        MetricsLogger.action(mContext, getMetricsCategory(), !isEnabled);
        mController.setHotspotEnabled(!isEnabled);
        mEnable.setAllowAnimation(true);
        mDisable.setAllowAnimation(true);
        }
    }

    @Override
    protected void handleLongClick() {
        mHost.startActivityDismissingKeyguard(TETHER_SETTINGS);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = mController.isHotspotSupported();

        if (arg instanceof Boolean) {
            state.value = (boolean) arg;
        } else {
            state.value = mController.isHotspotEnabled();
        }
        if (state.visible && state.value) {
            final List<WifiDevice> clients = mConnectivityManager.getTetherConnectedSta();
            final int count = clients != null ? clients.size() : 0;
            state.label = mContext.getResources().getQuantityString(
                    R.plurals.wifi_hotspot_connected_clients_label, count, count);
        } else {
            state.label = mContext.getString(R.string.quick_settings_hotspot_label);
        }
        state.icon = state.visible && state.value ? mEnable : mDisable;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsLogger.QS_HOTSPOT;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(R.string.accessibility_quick_settings_hotspot_changed_on);
        } else {
            return mContext.getString(R.string.accessibility_quick_settings_hotspot_changed_off);
        }
    }

    private static UsageTracker newUsageTracker(Context context) {
        return new UsageTracker(context, Prefs.Key.HOTSPOT_TILE_LAST_USED, HotspotTile.class,
                R.integer.days_to_show_hotspot_tile);
    }

    private BroadcastReceiver mTetherConnectStateChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshState();
        }
    };

    private final class Callback implements HotspotController.Callback {
        @Override
        public void onHotspotChanged(boolean enabled) {
            refreshState(enabled);
        }
    };

    /**
     * This will catch broadcasts for changes in hotspot state so we can show
     * the hotspot tile for a number of days after use.
     */
    public static class APChangedReceiver extends BroadcastReceiver {
        private UsageTracker mUsageTracker;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (mUsageTracker == null) {
                mUsageTracker = newUsageTracker(context);
            }
            mUsageTracker.trackUsage();
        }
    }
}
