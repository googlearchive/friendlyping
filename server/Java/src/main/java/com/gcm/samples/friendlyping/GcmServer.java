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
import com.google.gson.JsonParser;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.StringUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class GcmServer {

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

  public static final String GCM_NAMESPACE = "google:mobile:data";
  public static final String GCM_ELEMENT_NAME = "gcm";
  public static final String GCM_HOST = "gcm.googleapis.com";
  public static final int GCM_CCS_PORT = 5235;

  private static final Logger logger = Logger.getLogger("GcmServer");

  private SmackCcsClient smackCcsClient;
  private Gson gson;
  private JsonParser jsonParser;
  // Filter to determine what messages get handled here, passed to external handler or ignored.
  private StanzaFilter stanzaFilter;
  // Handle normal, ack, nack and control type, incoming GCM messages. For normal messages,
  // call onMessage to be handled externally. For other message types log their receipt but
  // more involved handling could be done.
  private StanzaListener stanzaListener;

  public GcmServer(String apiKey, String senderId, String serviceName) {
    jsonParser = new JsonParser();
    gson = new GsonBuilder().create();
    String username = senderId + "@" + GCM_HOST;
    smackCcsClient = new SmackCcsClient(apiKey, username, serviceName, GCM_HOST, GCM_CCS_PORT);

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

    stanzaFilter = new StanzaFilter() {
      @Override
      public boolean accept(Stanza stanza) {
        // Accept messages from GCM CCS.
        if (stanza.hasExtension(GCM_ELEMENT_NAME, GCM_NAMESPACE)) {
          return true;
        }
        // Reject messages that are not from GCM CCS.
        return false;
      }
    };

    stanzaListener = new StanzaListener() {
      @Override
      public void processPacket(Stanza packet) throws SmackException.NotConnectedException {
        // Extract the GCM message from the packet.
        GcmPacketExtension packetExtension =
            (GcmPacketExtension) packet.getExtension(GCM_NAMESPACE);

        JsonObject jGcmMessage = jsonParser.parse(packetExtension.getJson()).getAsJsonObject();
        String from = jGcmMessage.get("from").getAsString();

        // If there is no message_type normal GCM message is assumed.
        if (!jGcmMessage.has("message_type")) {
          if (StringUtils.isNotEmpty(from)) {
            JsonObject jData = jGcmMessage.get("data").getAsJsonObject();
            onMessage(from, jData);

            // Send Ack to CCS to confirm receipt of upstream message.
            String messageId = jGcmMessage.get("message_id").getAsString();
            if (StringUtils.isNotEmpty(messageId)) {
              sendAck(from, messageId);
            } else {
              logger.log(Level.SEVERE, "Message ID is null or empty.");
            }
          } else {
            logger.log(Level.SEVERE, "From is null or empty.");
          }
        } else {
          // Handle message_type here.
          String messageType = jGcmMessage.get("message_type").getAsString();
          if (messageType.equals("ack")) {
            // Handle ACK. Here the ack is logged, you may want to further process the ACK at this
            // point.
            String messageId = jGcmMessage.get("message_id").getAsString();
            logger.info("ACK received for message " + messageId + " from " + from);
          } else if (messageType.equals("nack")) {
            // Handle NACK. Here the nack is logged, you may want to further process the NACK at
            // this point.
            String messageId = jGcmMessage.get("message_id").getAsString();
            logger.info("NACK received for message " + messageId + " from " + from);
          } else if (messageType.equals("control")) {
            logger.info("Control message received.");
            String controlType = jGcmMessage.get("control_type").getAsString();
            if (controlType.equals("CONNECTION_DRAINING")) {
              // Handle connection draining
              // SmackCcsClient only maintains one connection the CCS to reduce complexity. A real
              // world application should be capable of maintaining multiple connections to GCM,
              // allowing the application to continue to onMessage for incoming messages on the
              // draining connection and sending all new out going messages on a newly created
              // connection.
              logger.info("Current connection will be closed soon.");
            } else {
              // Currently the only control_type is CONNECTION_DRAINING, if new control messages
              // are added they should be handled here.
              logger.info("New control message has been received.");
            }
          }
        }
      }
    };

    smackCcsClient.listen(stanzaListener, stanzaFilter);
  }

  /**
   * Define the handling of received upstream GCM message data. Subclass should provide concrete
   * implementation.
   *
   * @param from Sender of the upstream message.
   * @param jData JSON data representing the payload of the GCM message.
   */
  public abstract void onMessage(String from, JsonObject jData);

  /**
   * Send messages to recipient via GCM.
   *
   * @param to Message recipient.
   * @param message Message to be sent.
   */
  public void send(String to, JsonObject message) {
    message.addProperty("to", to);
    /**
     * Message ID generated as a remainder of current time in milliseconds. You could use any
     * method of unique ID generation here.
     */
    message.addProperty("message_id", (Calendar.getInstance().getTimeInMillis()) + "");

    final String payload = gson.toJson(message);
    Stanza stanza = new Stanza() {
      @Override
      public CharSequence toXML() {
        return wrapWithXML(payload);
      }
    };

    logger.info("sending: " + stanza);
    smackCcsClient.sendStanza(stanza);
  }

  /**
   * Send Ack message back to CCS to acknowledged the receipt of the message with ID msg_id.
   *
   * @param to Registration token of the sender of the message being acknowledged.
   * @param msg_id ID of message being acknowledged.
   */
  private void sendAck(String to, String msg_id) {
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

    smackCcsClient.sendStanza(stanza);
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
