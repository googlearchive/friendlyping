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

package com.google.samples.apps.friendlyping.model;

/**
 * Events that can be tracked via Google Analytics.
 */
public enum TrackingEvent {
    USER_LOGIN("User", "Login"),
    USER_LOGOUT("User", "Logout"),
    PING_SENT("Ping", "Sent");

    private String mCategory;
    private String mAction;

    TrackingEvent(String category, String action) {
        mCategory = category;
        mAction = action;
    }

    public String getCategory() {
        return mCategory;
    }

    public String getAction() {
        return mAction;
    }
}
