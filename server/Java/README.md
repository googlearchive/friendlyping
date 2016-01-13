Java Friendly Ping Server.

This Friendly Ping Server uses the [Smack XMPP library (v4.1.0)][1] to manage connections to CCS. All
messages are sent and received via CCS.

##Build

In `FriendlyPingServer.java`, replace the `<SENDER_ID>` and `<SERVER_API_KEY>` placeholders with the values
for your project.

You can get your Server API Key and Sender ID for your project from its **Download and install configuration** page at https://developers.google.com/mobile/add.


	./gradlew build
 

##Run
	./gradlew run

[1]: https://community.igniterealtime.org/blogs/ignite/2015/03/29/smack-410-released