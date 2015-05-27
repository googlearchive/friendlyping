//
//  Copyright (c) 2015 Google Inc.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

import UIKit

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate, GCMReceiverDelegate {

  var window: UIWindow?
  var connectedToGcm = false
  var subscribed = false
  var registeredToFP = false
  var registrationToken: String?
  var gcmSenderID: String?
  var topic = "/topics/newclient"

  let registrationKey = "onRegistrationCompleted"
  let messageKey = "onMessageReceived"

  func application(application: UIApplication, didFinishLaunchingWithOptions
      launchOptions: [NSObject: AnyObject]?) -> Bool {
    var configureError:NSError?
    GGLContext.sharedInstance().configureWithError(&configureError)
    if configureError != nil {
      println("Error configuring the Google context: \(configureError)")
    }
    gcmSenderID = GGLContext.sharedInstance().configuration.gcmSenderID
    var types: UIUserNotificationType = UIUserNotificationType.Badge |
        UIUserNotificationType.Alert |
        UIUserNotificationType.Sound
    var settings: UIUserNotificationSettings =
    UIUserNotificationSettings( forTypes: types, categories: nil )
    application.registerUserNotificationSettings(settings)
    application.registerForRemoteNotifications()
    var config = GCMConfig.defaultConfig()
    config.receiverDelegate = self
    config.logLevel = GCMLogLevel.Debug
    GCMService.sharedInstance().startWithConfig(config)
    return true
  }

  func applicationDidBecomeActive( application: UIApplication) {
    GCMService.sharedInstance().connectWithHandler({
      (NSError error) -> Void in
      if error != nil {
        println("Could not connect to GCM: \(error.localizedDescription)")
      } else {
        println("Connected to GCM")
        self.connectedToGcm = true
        self.connectToFriendlyPing()
      }
    })
  }

  func applicationDidEnterBackground(application: UIApplication) {
    GCMService.sharedInstance().disconnect()
    self.connectedToGcm = false
  }

  func application( application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken
    deviceToken: NSData ) {
      GGLInstanceID.sharedInstance().startWithConfig(GGLInstanceIDConfig.defaultConfig())
      var registrationOptions = [kGGLInstanceIDRegisterAPNSOption:deviceToken,
        kGGLInstanceIDAPNSServerTypeSandboxOption:true]
      GGLInstanceID.sharedInstance().tokenWithAuthorizedEntity(gcmSenderID, scope: kGGLInstanceIDScopeGCM,
        options: registrationOptions, handler: registrationHandler)
  }

  func application( application: UIApplication, didFailToRegisterForRemoteNotificationsWithError
    error: NSError ) {
      println("Registration for remote notification failed with error: \(error.localizedDescription)")
      let userInfo = ["error": error.localizedDescription]
      NSNotificationCenter.defaultCenter().postNotificationName(
        registrationKey, object: nil, userInfo: userInfo)
  }

  func registrationHandler(registrationToken: String!, error: NSError!) {
    if (registrationToken != nil) {
      self.registrationToken = registrationToken;
      connectToFriendlyPing()
      let userInfo = ["registrationToken": registrationToken]
      NSNotificationCenter.defaultCenter().postNotificationName(
        self.registrationKey, object: nil, userInfo: userInfo)
    } else {
      println("Registration to GCM failed with error: \(error.localizedDescription)")
      let userInfo = ["error": error.localizedDescription]
      NSNotificationCenter.defaultCenter().postNotificationName(
        self.registrationKey, object: nil, userInfo: userInfo)
    }
  }

  func application( application: UIApplication,
    didReceiveRemoteNotification userInfo: [NSObject : AnyObject]) {
      GCMService.sharedInstance().appDidReceiveMessage(userInfo);
      NSNotificationCenter.defaultCenter().postNotificationName(
        self.messageKey, object: nil, userInfo: userInfo)
  }

  func connectToFriendlyPing() {
    if connectedToGcm && registrationToken != nil {
      subscribeToTopic()
      registerToFriendlyPing()
    }
  }

  func subscribeToTopic() {
    if !subscribed {
      GCMPubSub().subscribeWithToken(registrationToken!, topic: topic, options: nil, handler: {
          (NSError error) -> Void in
        if (error != nil) {
          // TODO(silvano): should this be more fatal? is the library retrying automatically?
          println("Error subscribing: \(error)")
          println("Topic subscription failed with error: \(error.localizedDescription)")
          let userInfo = ["error": error.localizedDescription]
          NSNotificationCenter.defaultCenter().postNotificationName(
            self.registrationKey, object: nil, userInfo: userInfo)
        } else {
          self.subscribed = true
        }
      })
    }
  }

  func registerToFriendlyPing() {
    if !registeredToFP {
      let data = ["action": "register_new_client", "name": "Silvano",
          "registration_token": self.registrationToken!, "profile_picture_url": "profile.jpg"]
      var messageId = NSProcessInfo.processInfo().globallyUniqueString
      GCMService.sharedInstance().sendMessage(data, to: "\(self.gcmSenderID!)@gcm.googleapis.com",
          withId: messageId)
      // TODO(silvano): should we set this upon reception of the client list?
      registeredToFP = true
    }
  }

  // TODO(silvano): do we actually need the message tracking in FP?
  func willSendDataMessageWithID(messageID: String, error: NSError) {
    println("Error sending message \(messageID): \(error)")
  }

  func didSendDataMessageWithID(messageID: String) {
    println("Message \(messageID) successfully sent")
  }

  func didDeleteMessagesOnServer() {
    println("Do something")
  }
}