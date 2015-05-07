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

/**
 * The protocol for use with our server / other clients.
 * These are being sent as keys within the JSON and indicate what the server / other client wants
 * to tell this client.
 */
public class GcmAction {

    public static final String REGISTER_NEW_CLIENT = "register_new_client";
    public static final String BROADCAST_NEW_CLIENT = "broadcast_new_client";
    public static final String SEND_CLIENT_LIST = "send_client_list";
    public static final String PING_CLIENT = "ping_client";
}
