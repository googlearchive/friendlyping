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

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.gcm.GcmPubSub;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.model.people.Person;
import com.google.samples.apps.friendlyping.constants.PingerKeys;
import com.google.samples.apps.friendlyping.R;
import com.google.samples.apps.friendlyping.constants.RegistrationConstants;
import com.google.samples.apps.friendlyping.util.FriendlyPingUtil;

import java.io.IOException;

/**
 * Deal with registration of the user with the GCM instance.
 */
public class RegistrationIntentService extends IntentService {

    private static final String TAG = "RegIntentService";

    public RegistrationIntentService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(this);
        try {
            // Just in case that onHandleIntent has been triggered several times in short
            // succession.
            synchronized (TAG) {
                InstanceID instanceID = InstanceID.getInstance(this);
                String token = instanceID.getToken(getString(R.string.gcm_defaultSenderId),
                        GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
                Log.d(TAG, "GCM registration token: " + token);

                // Register to the server and subscribe to the topic of interest.
                sendRegistrationToServer(token);
                // The list of topics we can subscribe to is being implemented within the server.
                GcmPubSub.getInstance(this).subscribe(token, "/topics/newclient", null);

                final SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(RegistrationConstants.SENT_TOKEN_TO_SERVER, true);
                editor.putString(RegistrationConstants.TOKEN, token);
                editor.apply();
            }
        } catch (IOException e) {
            Log.d(TAG, "Failed to complete token refresh", e);
            sharedPreferences.edit().putBoolean(RegistrationConstants.
                    SENT_TOKEN_TO_SERVER, false).apply();

        }
        Intent registrationComplete = new Intent(RegistrationConstants.REGISTRATION_COMPLETE);
        LocalBroadcastManager.getInstance(this).sendBroadcast(registrationComplete);
    }

    /**
     * Sends the registration to the server.
     *
     * @param token The token to send.
     * @throws IOException Thrown when a connection issue occurs.
     */
    private void sendRegistrationToServer(String token) throws IOException {
        final GoogleApiClient googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Plus.API)
                .addScope(Plus.SCOPE_PLUS_PROFILE)
                .build();
        googleApiClient.blockingConnect();

        Bundle registration = createRegistrationBundle(googleApiClient);
        registration.putString(PingerKeys.REGISTRATION_TOKEN, token);

        // Register the user at the server.
        GoogleCloudMessaging.getInstance(this).send(FriendlyPingUtil.getServerUrl(this),
                String.valueOf(System.currentTimeMillis()), registration);
    }

    /**
     * Creates the registration bundle and fills it with user information.
     *
     * @param googleApiClient The connected api client.
     * @return A bundle with registration data.
     */
    private Bundle createRegistrationBundle(GoogleApiClient googleApiClient) {
        // Get the current user's information for registration
        final Person currentPerson = Plus.PeopleApi.getCurrentPerson(googleApiClient);
        final String displayName;
        final String profilePictureUrl;

        Bundle registration = new Bundle();
        if (currentPerson != null) {
            displayName = currentPerson.getDisplayName();
            profilePictureUrl = currentPerson.getImage().getUrl();
        } else {
            Log.e(TAG, "Couldn't load person. Falling back to default.");
            Log.d(TAG, "Make sure that the Google+ API is enabled for your project.");
            Log.d(TAG, "More information can be found here: "
                    + "https://developers.google.com/+/mobile/android/"
                    + "getting-started#step_1_enable_the_google_api");
            displayName = "Anonymous Kitten";
            profilePictureUrl = "http://placekitten.com/g/500/500";
        }

        // Create the bundle for registration with the server.
        registration.putString(PingerKeys.ACTION, GcmAction.REGISTER_NEW_CLIENT);
        registration.putString(PingerKeys.NAME, displayName);
        registration.putString(PingerKeys.PICTURE_URL, profilePictureUrl);
        return registration;
    }

}
