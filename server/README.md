golang Friendly Ping
--

## Installation
Install protobuf for go
    $ go get github.com/golang/protobuf/proto
Copy ali's demon to `github.com/aliafshar/gcm` in the `src` folder of your `GOPATH`, and go get its dependencies (won't be needed when the deamon is published).

## Usage

    $ go run server.go -apiKey AIzaSyB8bwYxv2YDn663HoOrkg0yCaYkesVcoKo \
    -senderId 177545629583 -testData testdata/clients.json

## Usage from clients

The current implementation is based on passing the protobuffers as base64 encoded strings inside the `base64` field of the message content.
For example, in Objective C you can register a new client doing:

```
Client *thisApp = [[[[[Client builder] setName:@"pippo"]
                        setRegistrationToken: _registrationToken]
                        setProfilePictureUrl:@"profile.jpg"] build];

RegisterNewClient *messagePayload = [[[RegisterNewClient builder] setClient:thisApp] build];
FriendlyPingMessage *messageProto = [[[[FriendlyPingMessage builder]
                                    setRncPayload:messagePayload]
                                    setAction:ActionRegisterNewClient] build];
NSDictionary *message = @{@"base64": [[messageProto data] base64EncodedStringWithOptions:0]};
NSString *to = [NSString stringWithFormat:@"%@@gcm.googleapis.com", _gcmSenderID];
NSString *messageId = [[NSProcessInfo processInfo] globallyUniqueString];
[[GCMService sharedInstance] sendMessage: message to: to withId: messageId];
```

And receive the list of connected clients:

```
- (void)application:(UIApplication *)application
    didReceiveRemoteNotification:(NSDictionary *)userInfo {
  //NSLog(@"Notification received: %@", userInfo);
  NSData *data = [[NSData alloc] initWithBase64EncodedString:userInfo[@"base64"] options:0];
  FriendlyPingMessage* message = [FriendlyPingMessage parseFromData:data];
  NSLog(@"Received FP message: %@", message);
  NSLog(@"Hello Steven Kan: %@", [message.sclPayload clientsAtIndex:2]);
}
```
