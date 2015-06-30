/*
 * Copyright Google Inc.
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

package com.google.samples.apps.friendlyping.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.samples.apps.friendlyping.AnalyticsHelper;
import com.google.samples.apps.friendlyping.PingerAdapter;
import com.google.samples.apps.friendlyping.R;
import com.google.samples.apps.friendlyping.constants.IntentExtras;
import com.google.samples.apps.friendlyping.constants.PingerKeys;
import com.google.samples.apps.friendlyping.constants.RegistrationConstants;
import com.google.samples.apps.friendlyping.gcm.GcmAction;
import com.google.samples.apps.friendlyping.gcm.RegistrationIntentService;
import com.google.samples.apps.friendlyping.model.Pinger;
import com.google.samples.apps.friendlyping.model.TrackingEvent;

import java.io.IOException;
import java.util.ArrayList;

/**
 * A fragment that displays a list of {@link Pinger}s, received and sent pings.
 */
public class FriendlyPingFragment extends Fragment {

    private static final String TAG = "FriendlyPingFragment";
    private static final String KEY_PINGERS = "key.pingers";

    private BroadcastReceiver mRegistrationBroadcastReceiver;
    private AdapterView.OnItemClickListener mOnItemClickListener;
    private SharedPreferences mDefaultSharedPreferences;
    private PingerAdapter mPingerAdapter;

    public FriendlyPingFragment() {
        mOnItemClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mPingerAdapter.getItems() == null) {
                    Log.w(TAG, "Pingers are not initialized, skipping send.");
                    return;
                }
                Pinger pinger = mPingerAdapter.getItem(position);
                pingSomeone(pinger);
            }
        };
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        final FragmentActivity activity = getActivity();

        mRegistrationBroadcastReceiver = new FriendlyPingBroadcastReceiver();
        mDefaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        // Start IntentService to register this application with GCM.
        Intent service = new Intent(activity, RegistrationIntentService.class);
        activity.startService(service);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mPingerAdapter == null) {
            return;
        }
        ArrayList<Pinger> tmpItems = mPingerAdapter.getItems();
        if (tmpItems != null) {
            outState.putParcelableArrayList(KEY_PINGERS, tmpItems);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        final IntentFilter filter = new IntentFilter();
        filter.addAction(RegistrationConstants.REGISTRATION_COMPLETE);
        filter.addAction(GcmAction.SEND_CLIENT_LIST);
        filter.addAction(GcmAction.BROADCAST_NEW_CLIENT);
        LocalBroadcastManager.getInstance(getActivity())
                .registerReceiver(mRegistrationBroadcastReceiver, filter);
    }

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(getActivity())
                .unregisterReceiver(mRegistrationBroadcastReceiver);
        super.onPause();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friendly_ping, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // Get the list view and set it up.
        ListView listView = (ListView) view.findViewById(R.id.ping_list);
        listView.setOnItemClickListener(mOnItemClickListener);
        listView.setEmptyView(view.findViewById(android.R.id.empty));
        if (listView.getAdapter() == null) {
            mPingerAdapter = new PingerAdapter(getActivity());
            listView.setAdapter(mPingerAdapter);
        }
        // Restore previously saved data.
        if (savedInstanceState != null) {
            ArrayList<Pinger> tmpPingers = savedInstanceState.getParcelableArrayList(KEY_PINGERS);
            if (tmpPingers != null) {
                mPingerAdapter = new PingerAdapter(view.getContext(), tmpPingers);
                listView.setAdapter(mPingerAdapter);
            }
        }

        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (null != activity) {
            // Setting the status bar color and Toolbar as ActionBar requires API 21+.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                activity.setSupportActionBar((Toolbar) view.findViewById(R.id.toolbar_ping));
                //noinspection ConstantConditions
                activity.getSupportActionBar().setDisplayShowTitleEnabled(false);
                activity.getWindow().setStatusBarColor(
                        getResources().getColor(R.color.primary_dark));
            }
        }
        // [START show_ad]
        AdView adView = (AdView) view.findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);
        // [END show_ad]
    }

    /**
     * Ping another registered client
     */
    private void pingSomeone(Pinger pinger) {
        final Context context = getActivity();
        Bundle data = new Bundle();
        data.putString(PingerKeys.ACTION, GcmAction.PING_CLIENT);
        data.putString(PingerKeys.TO, pinger.getRegistrationToken());
        data.putString(PingerKeys.SENDER,
                mDefaultSharedPreferences.getString(RegistrationConstants.TOKEN, null));
        try {
            GoogleCloudMessaging.getInstance(context)
                    .send(pinger.getRegistrationToken(), String.valueOf(System.currentTimeMillis()),
                            data);
            AnalyticsHelper.send(context, TrackingEvent.PING_SENT);
        } catch (IOException e) {
            Log.w(TAG, "Could not ping client.", e);
        }
        mPingerAdapter.moveToTop(pinger);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        getActivity().getMenuInflater().inflate(R.menu.menu_friendly_ping, menu);
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    /**
     * Receive broadcasts from GCM and other pingers.
     */

    private class FriendlyPingBroadcastReceiver extends BroadcastReceiver {

        private static final String TAG = "PingerBroadcastReceiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "onReceive: " + action);
            switch (action) {
                case RegistrationConstants.REGISTRATION_COMPLETE:
                    handleRegistrationComplete(context);
                    break;
                case GcmAction.SEND_CLIENT_LIST:
                    final ArrayList<Pinger> tmpPingers = intent
                            .getParcelableArrayListExtra(IntentExtras.PINGERS);
                    mPingerAdapter.addPinger(tmpPingers);
                    break;
                case GcmAction.BROADCAST_NEW_CLIENT:
                    Pinger pinger = intent.getParcelableExtra(IntentExtras.NEW_PINGER);
                    mPingerAdapter.addPinger(pinger);
                    break;
            }
        }

        private void handleRegistrationComplete(Context context) {
            SharedPreferences sharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(context);
            boolean tokenHasBeenSent = sharedPreferences
                    .getBoolean(RegistrationConstants.SENT_TOKEN_TO_SERVER, false);
            if (tokenHasBeenSent) {
                Log.d(TAG, "onReceive: Token has been sent");
            } else {
                Log.e(TAG, "onReceive: Couldn't send token");
            }
        }
    }
}
