golang Friendly Ping
--

## Installation
Install the gcm demon:

    $ go get -a github.com//aliafshar/gcm


## Usage

    $ go run server.go -apiKey AIzaSyB8bwYxv2YDn663HoOrkg0yCaYkesVcoKo \
    -senderId 177545629583 -testData testdata/clients.json

## Usage from clients

From iOS Swift, this is how you register a new client:

```
let data = ["action": "register_new_client", "name": "Silvano", "registration_token": registrationToken!, "profile_picture_url": "profile.jpg"]
var messageId = NSProcessInfo.processInfo().globallyUniqueString
GCMService.sharedInstance().sendMessage(data, to: "\(gcmSenderID)@gcm.googleapis.com", withId: messageId)
```

To parse the list of clients sent by the server:

```
func application( application: UIApplication,
    didReceiveRemoteNotification userInfo: [NSObject : AnyObject]) {
  var clients = userInfo["clients"]!
  println("Notification received: \(clients)")
}
```