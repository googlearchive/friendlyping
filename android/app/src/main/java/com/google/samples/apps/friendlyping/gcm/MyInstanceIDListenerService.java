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

import com.google.android.gms.iid.InstanceIDListenerService;

/**
 * Listen for changes of the instance id and forward them.
 */
public class MyInstanceIDListenerService extends InstanceIDListenerService {

    /**
     * Called if server rotates InstanceID token. This may occur if the security of
     * the previous token had been compromised. This call is initiated by the
     * InstanceID provider.
     *
     * @param updateID If true a new InstanceID is issued. If false only the token
     * has been updated.
     */
    @Override
    public void onTokenRefresh(boolean updateID) {
        // Fetch updated Instance ID token and notify our app's server of any changes
        // (if applicable).
        Intent intent = new Intent(this, RegistrationIntentService.class);
        startService(intent);
    }
}
