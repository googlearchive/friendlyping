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
import com.google.android.gms.common.api.GoogleApiClient;
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
    private GoogleApiClient mGoogleApiClient;
    private BroadcastReceiver mRegistrationBroadcastReceiver;
    private ListView mListView;
    private ArrayList<Pinger> mPingers;

    public FriendlyPingFragment() {
        /* no-op */
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mRegistrationBroadcastReceiver = new PingerBroadcastReceiver();
        // Start IntentService to register this application with GCM.
        Intent service = new Intent(getActivity(), RegistrationIntentService.class);
        getActivity().startService(service);
    }

    @Override
    public void onResume() {
        super.onResume();
        final IntentFilter filter = new IntentFilter();
        filter.addAction(RegistrationConstants.REGISTRATION_COMPLETE);
        filter.addAction(GcmAction.SEND_CLIENT_LIST);
        // TODO: 5/7/15 add other implemented actions
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
        mListView = (ListView) view.findViewById(R.id.ping_list);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final String pingerId = mPingers.get(position).getRegistrationToken();
                pingSomeone(pingerId);
            }
        });
        mListView.setEmptyView(view.findViewById(android.R.id.empty));
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (null != activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                activity.setSupportActionBar((Toolbar) view.findViewById(R.id.toolbar_ping));
                //noinspection ConstantConditions
                activity.getSupportActionBar().setDisplayShowTitleEnabled(false);
                activity.getWindow().setStatusBarColor(
                        getResources().getColor(R.color.primary_dark));
            }
        }

        // TODO: 4/23/15 set adapter to display received pings
        final String deviceId = getString(R.string.test_device_id);
        AdView adView = (AdView) view.findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().
                addTestDevice(deviceId).build();
        adView.loadAd(adRequest);
    }

    /**
     * Pings another client
     * @param registrationId The registration id of the client to ping.
     */
    private void pingSomeone(String registrationId) {
        Bundle data = new Bundle();
        data.putString(PingerKeys.ACTION, GcmAction.PING_CLIENT);
        try {
            final Context context = getActivity();
            GoogleCloudMessaging.getInstance(context)
                    .send(registrationId, String.valueOf(System.currentTimeMillis()), data);
            AnalyticsHelper.send(context, TrackingEvent.PING_SENT);
        } catch (IOException e) {
            Log.w(TAG, "Could not ping client.", e);
        }
    }

    public void setGoogleApiClient(GoogleApiClient googleApiClient) {
        mGoogleApiClient = googleApiClient;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        getActivity().getMenuInflater().inflate(R.menu.menu_friendly_ping, menu);
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    /**
     * The {@link BroadcastReceiver} that will receive broadcasts from Gcm and other pingers.
     */
    private class PingerBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case RegistrationConstants.REGISTRATION_COMPLETE:
                    handleRegistrationComplete(context);
                    break;
                case GcmAction.SEND_CLIENT_LIST:
                    mPingers = intent.getParcelableArrayListExtra(IntentExtras.PINGERS);
                    mListView.setAdapter(new PingerAdapter(getActivity(), mPingers));
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
