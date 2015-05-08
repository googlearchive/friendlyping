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
import com.google.gson.JsonObject;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SmackCcsClient provides communication with GCM Cloud Connection Server (XMPP Server).
 * This sample uses Smack version 4.1.0.
 */
public class SmackCssClient {

  private static final Logger logger = Logger.getLogger("SmackCssClient");

  public static final String GCM_NAMESPACE = "google:mobile:data";
  public static final String GCM_ELEMENT_NAME = "gcm";
  public static final String GCM_HOST = "gcm.googleapis.com";
  public static final int GCM_CCS_PORT = 5235;

  /**
   * Extension of Packet to allow production and consumption of packets, to and from GCM.
   */
  public class GcmPacketExtension implements ExtensionElement {

    private String json;

    public GcmPacketExtension(String json) {
      this.json = json;
    }

    public String getJson() {
      return json;
    }

    @Override
    public String getNamespace() {
      return GCM_NAMESPACE;
    }

    @Override
    public String getElementName() {
      return GCM_ELEMENT_NAME;
    }

    @Override
    public CharSequence toXML() {
      return String.format("<%s xmlns=\"%s\">%s</%s>", getElementName(), getNamespace(), json,
          getElementName());
    }
  }

  public AbstractXMPPConnection connection;

  public SmackCssClient(String apiKey, String senderId, String serviceName) {
    XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
        .setServiceName(serviceName)
        .setHost(GCM_HOST)
        .setSocketFactory(SSLSocketFactory.getDefault())
        .setSendPresence(false)
        .setPort(GCM_CCS_PORT)
        .build();

    connection = new XMPPTCPConnection(config);
    Roster.getInstanceFor(connection).setRosterLoadedAtLogin(false);

    connection.addConnectionListener(new ConnectionListener() {
      @Override
      public void connected(XMPPConnection connection) {
        logger.info("Connected to CCS");
      }

      @Override
      public void authenticated(XMPPConnection connection, boolean resumed) {
        logger.info("Authenticated with CCS");
      }

      @Override
      public void connectionClosed() {
        logger.info("Connection to CCS closed");
      }

      @Override
      public void connectionClosedOnError(Exception e) {
        logger.log(Level.WARNING, "Connection closed because of an error.", e);
      }

      @Override
      public void reconnectionSuccessful() {
        logger.info("Reconnected to CCS");
      }

      @Override
      public void reconnectingIn(int seconds) {
        logger.info("Reconnecting to CCS in " + seconds);
      }

      @Override
      public void reconnectionFailed(Exception e) {
        logger.log(Level.WARNING, "Reconnection to CCS failed", e);
      }
    });

    // Add the GcmPacketExtension as an extension provider.
    ProviderManager.addExtensionProvider(GCM_ELEMENT_NAME, GCM_NAMESPACE,
        new ExtensionElementProvider<GcmPacketExtension>() {
      @Override
      public GcmPacketExtension parse(XmlPullParser parser, int initialDepth)
          throws XmlPullParserException, IOException, SmackException {
        String json = parser.nextText();
        return new GcmPacketExtension(json);
      }
    });

    try {
      // Connect and authenticate with to GCM CCS.
      connection.connect();
      connection.login(senderId + "@gcm.googleapis.com", apiKey);
    } catch (SmackException | IOException | XMPPException e) {
      logger.log(Level.SEVERE, "Unable to connect or login to GCM CCS.", e);
    }
  }

  /**
   * Begin listening for incoming messages.
   *
   * @param stanzaListener Listener that handles accepted messages. This is defined in
   *                       FriendlyPingServer.
   */
  public void listen(StanzaListener stanzaListener) {
    connection.addAsyncStanzaListener(stanzaListener, new StanzaFilter() {
      @Override
      public boolean accept(Stanza stanza) {
        // Accept messages from GCM CCS.
        if (stanza.hasExtension(GCM_ELEMENT_NAME, GCM_NAMESPACE)) {
          return true;
        }
        // Reject messages that are not from GCM CCS.
        return false;
      }
    });
    logger.info("Listening for incoming XMPP Stanzas...");
  }

  /**
   * Send messages to recipient via GCM.
   *
   * @param to Message recipient.
   * @param message Message to be sent.
   */
  public void send(String to, JsonObject message) {
    try {
      Gson gson = new GsonBuilder().create();
      JsonObject jPayload = new JsonObject();
      jPayload.addProperty("to", to);
      /**
       * Message ID generated as a remainder of current time in milliseconds. You could use any
       * method of unique generation here.
       */
      jPayload.addProperty("message_id", (Calendar.getInstance().getTimeInMillis()) + "");
      jPayload.add("data", message);

      final String payload = gson.toJson(jPayload);
      Stanza stanza = new Stanza() {
        @Override
        public CharSequence toXML() {
          return wrapWithXML(payload);
        }
      };

      System.out.println("sending: " + stanza);
      connection.sendStanza(stanza);
    } catch (SmackException.NotConnectedException e) {
      e.printStackTrace();
    }
  }

  /**
   * Send Ack message back to CCS to acknowledged the receipt of the message with ID msg_id.
   *
   * @param to Registration token of the sender of the message being acknowledged.
   * @param msg_id ID of message being acknowledged.
   */
  public void sendAck(String to, String msg_id) {
    try {
      JsonObject jPayload = new JsonObject();
      jPayload.addProperty("to", to);
      jPayload.addProperty("message_id", msg_id);
      jPayload.addProperty("message_type", "ack");

      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      final String payload = gson.toJson(jPayload);
      Stanza stanza = new Stanza() {
        @Override
        public CharSequence toXML() {
          return wrapWithXML(payload);
        }
      };

      connection.sendStanza(stanza);
    } catch (SmackException.NotConnectedException e) {
      e.printStackTrace();
    }
  }

  /**
   * Wrap payload with appropriate xml for XMPP transport.
   * @param payload String to be wrapped.
   */
  private String wrapWithXML(String payload) {
    String msg = String.format("<message><%s xmlns=\"%s\">%s</%s></message>",
        GCM_ELEMENT_NAME, GCM_NAMESPACE, payload, GCM_ELEMENT_NAME);

    return msg;
  }
}
