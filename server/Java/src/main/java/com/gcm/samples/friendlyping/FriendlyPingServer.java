/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gcm.samples.friendlyping;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import org.jivesoftware.smack.util.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FriendlyPingServer provides the logic to allow clients to register and be notified of other
 * clients registering. It also responds to pings from clients.
 */
public class FriendlyPingServer {

  // FriendlyPing Client.
  private class Client {
    String name;
    @SerializedName("registration_token")
    String registrationToken;
    @SerializedName("profile_picture_url")
    String profilePictureUrl;

    public boolean isValid() {
      return StringUtils.isNotEmpty(name) && StringUtils.isNotEmpty(registrationToken) &&
          StringUtils.isNotEmpty(profilePictureUrl);
    }
  }

  // FriendlyGcmServer defines onMessage to handle incoming friendly ping messages.
  private class FriendlyGcmServer extends GcmServer {

    public FriendlyGcmServer (String apiKey, String senderId, String serviceName) {
      super(apiKey, senderId, serviceName);
    }

    @Override
    public void onMessage(String from, JsonObject jData) {
      if (jData.has("action")) {
        String action = jData.get("action").getAsString();
        if (action.equals("register_new_client")) {
          registerNewClient(jData);
        } else if (action.equals("ping_client")) {
          String toToken = jData.get("to").getAsString();
          String senderToken = jData.get("sender").getAsString();
          if (StringUtils.isNotEmpty(toToken) && StringUtils.isNotEmpty(senderToken) &&
              clientMap.containsKey(toToken) && clientMap.containsKey(senderToken)) {
            pingClient(toToken, senderToken);
          }
        }
      } else {
        logger.info("No action found. Message received missing action.");
      }
    }
  }

  private static final Logger logger = Logger.getLogger("FriendlyPingServer");

  private static final String SENDER_ID = "<SENDER_ID>";
  private static final String API_KEY = "<API_KEY>";
  private static final String NEW_CLIENT_TOPIC = "/topics/newuser";
  private static final String PING_TITLE = "Friendly Ping!";
  private static final String PING_ICON = "mipmap/ic_launcher";

  public static final String SERVICE_NAME = "Friendly Ping Server";

  // Store of clients registered with FriendlyPingServer.
  private Map<String, Client> clientMap;
  // Listener responsible for handling incoming registrations and pings.
  private FriendlyGcmServer friendlyGcmServer;
  // Fake Client is used to allow clients to send a test ping even if they do not have other
  // Clients registered.
  private Client fakeClient;

  // Gson helper to assist with going to and from JSON and Client.
  private Gson gson;

  public FriendlyPingServer(String apiKey, String senderId) {
    clientMap = new ConcurrentHashMap<String, Client>();

    initFakeClient();
    clientMap.put(fakeClient.registrationToken, fakeClient);

    gson = new GsonBuilder().create();

    friendlyGcmServer = new FriendlyGcmServer(apiKey, senderId, SERVICE_NAME);
  }

  /**
   * Set property values of FriendlyPingServer's fake client.
   */
  private void initFakeClient() {
    fakeClient = new Client();
    fakeClient.name = "Larry";
    fakeClient.registrationToken = SENDER_ID + "@gcm.googleapis.com";
    fakeClient.profilePictureUrl =
        "https://lh3.googleusercontent.com/-Y86IN-vEObo/AAAAAAAAAAI/AAAAAAADO1I/QzjOGHq5kNQ/photo.jpg?sz=50";
  }

  /**
   * Create Client from given JSON data, add client to client list, broadcast newly registered
   * client to all previously registered clients and send client list to new client.
   *
   * @param jData JSON data containing properties of new Client.
   */
  private void registerNewClient(JsonObject jData) {
    Client newClient = gson.fromJson(jData, Client.class);
    if (newClient.isValid()) {
      addClient(newClient);
      broadcastNewClient(newClient);
      sendClientList(newClient);
    } else {
      logger.log(Level.WARNING, "Could not unpack received data into a Client.");
    }
  }

  /**
   * Add given client to Map of Clients.
   *
   * @param client Client to be added.
   */
  private void addClient(Client client) {
    clientMap.put(client.registrationToken, client);
  }

  /**
   * Broadcast the newly registered client to clients that have already been registered. The
   * broadcast is sent via the PubSub topic "/topics/newuser" all registered clients should be
   * subscribed to this topic.
   *
   * @param client Newly registered client.
   */
  private void broadcastNewClient(Client client) {
    JsonObject jBroadcast = new JsonObject();

    JsonObject jData = new JsonObject();
    jData.addProperty("action", "broadcast_new_client");

    JsonObject jClient = gson.toJsonTree(client).getAsJsonObject();
    jData.add("client", jClient);

    jBroadcast.add("data", jData);
    friendlyGcmServer.send(NEW_CLIENT_TOPIC, jBroadcast);
  }

  /**
   * Send client list to newly registered client. When a new client is registered, that client must
   * be informed about the other registered clients.
   *
   * @param client Newly registered client.
   */
  private void sendClientList(Client client) {
    JsonElement clientElements = gson.toJsonTree(clientMap.values(),
        new TypeToken<Collection<Client>>() {}.getType());
    if (clientElements.isJsonArray()) {
      JsonObject jSendClientList = new JsonObject();

      JsonObject jData = new JsonObject();
      jData.addProperty("action", "send_client_list");
      jData.add("clients", clientElements);

      jSendClientList.add("data", jData);
      friendlyGcmServer.send(client.registrationToken, jSendClientList);
    }
  }

  /**
   * Send message to Client with matching toToken. The validity of to and sender tokens
   * should be check before this method is called.
   *
   * @param toToken Token of recipient of ping.
   * @param senderToken Token of sender of ping.
   */
  private void pingClient(String toToken, String senderToken) {
    // TODO(arthurthompson): Check if toToken is fake client and if so send ping to sender.
    Client senderClient = clientMap.get(senderToken);
    JsonObject jPing = new JsonObject();

    JsonObject jData = new JsonObject();
    jData.addProperty("action", "ping_client");
    jData.addProperty("sender", senderClient.registrationToken);

    JsonObject jNotification = new JsonObject();
    // title and body are required fields for Display Notifications on Android.
    // TODO(arthurthompson): Comment on required fields for Chrome and iOS.
    jNotification.addProperty("title", PING_TITLE);
    jNotification.addProperty("body", senderClient.name + " is pinging you.");
    jNotification.addProperty("icon", PING_ICON);
    jNotification.addProperty("sound", "default");
    jNotification.addProperty("click_action", "com.google.samples.apps.friendlyping.pingReceived");

    jPing.add("data", jData);
    jPing.add("notification", jNotification);

    friendlyGcmServer.send(toToken, jPing);
  }

  public static void main(String[] args) {
    // Initialize FriendlyPingServer with appropriate API Key and SenderID.
    new FriendlyPingServer(API_KEY, SENDER_ID);

    // Keep main thread alive.
    try {
      CountDownLatch latch = new CountDownLatch(1);
      latch.await();
    } catch (InterruptedException e) {
      logger.log(Level.SEVERE, "An error occurred while latch was waiting.", e);
    }
  }
}
