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

package com.google.samples.apps.friendlyping.gcm;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.gcm.GcmListenerService;
import com.google.samples.apps.friendlyping.constants.IntentExtras;
import com.google.samples.apps.friendlyping.model.Pinger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * This service listens for messages from GCM, makes them usable for this application and then
 * sends them to their destination.
 */
public class MyGcmListenerService extends GcmListenerService {

    private static final String TAG = "MyGcmListenerService";

    @Override
    public void onMessageReceived(String from, Bundle data) {
        if (from == null) {
            Log.w(TAG, "Couldn't determine origin of message. Skipping.");
            return;
        }
        try {
            digestData(data);
        } catch (JSONException e) {
            Log.e(TAG, "onMessageReceived: Could not digest data", e);
        }
    }

    private void digestData(Bundle data) throws JSONException {
        final String action = data.getString("action");
        Log.d(TAG, "Action: " + action);
        if (action == null) {
            Log.w(TAG, "onMessageReceived: Action was null, skipping further processing.");
            return;
        }
        Intent broadcastIntent = new Intent(action);
        switch (action) {
            case GcmAction.SEND_CLIENT_LIST:
                final ArrayList<Pinger> pingers = getPingers(data);
                broadcastIntent.putParcelableArrayListExtra(IntentExtras.PINGERS, pingers);
                break;
            case GcmAction.BROADCAST_NEW_CLIENT:
                Pinger newPinger = getNewPinger(data);
                broadcastIntent.putExtra(IntentExtras.NEW_PINGER, newPinger);
                break;
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
    }

    private ArrayList<Pinger> getPingers(Bundle data) throws JSONException {
        final JSONArray clients = new JSONArray(data.getString("clients"));
        ArrayList<Pinger> pingers = new ArrayList<>(clients.length());
        for (int i = 0; i < clients.length(); i++) {
            JSONObject jsonPinger = clients.getJSONObject(i);
            pingers.add(Pinger.fromJson(jsonPinger));
        }
        return pingers;
    }

    private Pinger getNewPinger(Bundle data) throws JSONException {
        final JSONObject client = new JSONObject(data.getString("client"));
        return Pinger.fromJson(client);
    }
}
