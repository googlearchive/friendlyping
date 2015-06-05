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
class AppDelegate: UIResponder, UIApplicationDelegate, GIDSignInDelegate, GCMReceiverDelegate {

  var window: UIWindow?

  func application(application: UIApplication, didFinishLaunchingWithOptions
      launchOptions: [NSObject: AnyObject]?) -> Bool {
    configureGGLContext()
    configureGCMService()
    registerForRemoteNotifications(application)
    return true
  }

  func configureGGLContext() {
    var configureError:NSError?
    GGLContext.sharedInstance().configureWithError(&configureError)
    if configureError != nil {
      println("Error configuring the Google context: \(configureError)")
    }
    GIDSignIn.sharedInstance().delegate = self
    let senderID = GGLContext.sharedInstance().configuration.gcmSenderID
    AppState.sharedInstance.senderID = senderID
    AppState.sharedInstance.serverAddress = "\(senderID)@gcm.googleapis.com"
  }

  func configureGCMService() {
    var config = GCMConfig.defaultConfig()
    config.receiverDelegate = self
    config.logLevel = GCMLogLevel.Debug
    GCMService.sharedInstance().startWithConfig(config)
  }

  func registerForRemoteNotifications(application: UIApplication) {
    var types: UIUserNotificationType = UIUserNotificationType.Badge |
      UIUserNotificationType.Alert |
      UIUserNotificationType.Sound
    var settings: UIUserNotificationSettings =
    UIUserNotificationSettings( forTypes: types, categories: nil )
    application.registerUserNotificationSettings(settings)
    application.registerForRemoteNotifications()
  }

  func application(application: UIApplication,
      openURL url: NSURL, sourceApplication: String?, annotation: AnyObject?) -> Bool {
    return GIDSignIn.sharedInstance().handleURL(url, sourceApplication: sourceApplication,
        annotation: annotation)
  }

  func signIn(signIn: GIDSignIn!, didSignInForUser user: GIDGoogleUser!,
      withError error: NSError!) {
    if (error == nil) {
      AnalyticsHelper.sendLoginEvent()
      AppState.sharedInstance.signedIn = true
      connectToFriendlyPing()
      NSNotificationCenter.defaultCenter().postNotificationName(Constants.NotificationKeys.SignedIn,
        object: nil, userInfo: nil)
    } else {
      println("\(error.localizedDescription)")
    }
  }

  func signIn(signIn: GIDSignIn!, didDisconnectWithUser user:GIDGoogleUser!,
      withError error: NSError!) {
    AppState.sharedInstance.signedIn = false
    AppState.sharedInstance.registeredToFP = false
  }

  func applicationDidBecomeActive( application: UIApplication) {
    GCMService.sharedInstance().connectWithHandler({
      (NSError error) -> Void in
      if error != nil {
        println("Could not connect to GCM: \(error.localizedDescription)")
      } else {
        println("Connected to GCM")
        AppState.sharedInstance.connectedToGcm = true
        self.connectToFriendlyPing()
      }
    })
  }

  func applicationDidEnterBackground(application: UIApplication) {
    GCMService.sharedInstance().disconnect()
    AppState.sharedInstance.connectedToGcm = false
  }

  func application( application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken
      deviceToken: NSData ) {
    GGLInstanceID.sharedInstance().startWithConfig(GGLInstanceIDConfig.defaultConfig())
    var registrationOptions = [kGGLInstanceIDRegisterAPNSOption:deviceToken,
        kGGLInstanceIDAPNSServerTypeSandboxOption:true]
    GGLInstanceID.sharedInstance().tokenWithAuthorizedEntity(AppState.sharedInstance.senderID,
        scope: kGGLInstanceIDScopeGCM, options: registrationOptions, handler: registrationHandler)
  }

  func application( application: UIApplication, didFailToRegisterForRemoteNotificationsWithError
      error: NSError ) {
    println("Registration for remote notification failed with error: \(error.localizedDescription)")
    let userInfo = ["error": error.localizedDescription]
    NSNotificationCenter.defaultCenter().postNotificationName(
        Constants.NotificationKeys.Registration, object: nil, userInfo: userInfo)
  }

  func registrationHandler(registrationToken: String!, error: NSError!) {
    if (registrationToken != nil) {
      AppState.sharedInstance.registrationToken = registrationToken;
      connectToFriendlyPing()
      let userInfo = ["registrationToken": registrationToken]
      NSNotificationCenter.defaultCenter().postNotificationName(
        Constants.NotificationKeys.Registration, object: nil, userInfo: userInfo)
    } else {
      println("Registration to GCM failed with error: \(error.localizedDescription)")
      let userInfo = ["error": error.localizedDescription]
      NSNotificationCenter.defaultCenter().postNotificationName(
        Constants.NotificationKeys.Registration, object: nil, userInfo: userInfo)
    }
  }

  func application( application: UIApplication,
      didReceiveRemoteNotification userInfo: [NSObject : AnyObject]) {
    GCMService.sharedInstance().appDidReceiveMessage(userInfo);
    NSNotificationCenter.defaultCenter().postNotificationName(
        Constants.NotificationKeys.Message, object: nil, userInfo: userInfo)
  }

  func application( application: UIApplication,
      didReceiveRemoteNotification userInfo: [NSObject : AnyObject],
      fetchCompletionHandler handler: (UIBackgroundFetchResult) -> Void) {
    GCMService.sharedInstance().appDidReceiveMessage(userInfo);
    NSNotificationCenter.defaultCenter().postNotificationName(
        Constants.NotificationKeys.Message, object: nil, userInfo: userInfo)
    handler(UIBackgroundFetchResult.NoData);
  }

  func connectToFriendlyPing() {
    if AppState.sharedInstance.connectedToGcm && AppState.sharedInstance.signedIn &&
        AppState.sharedInstance.registrationToken != nil {
      subscribeToTopic()
      registerToFriendlyPing()
    }
  }

  func subscribeToTopic() {
    if !AppState.sharedInstance.subscribed {
      GCMPubSub().subscribeWithToken(AppState.sharedInstance.registrationToken!,
          topic: Constants.GCMStrings.Topic, options: nil, handler: {
            (NSError error) -> Void in
              if (error != nil) {
                // TODO(silvano): treat already subscribed with more grace
                println("Error subscribing: \(error)")
                println("Topic subscription failed with error: \(error.localizedDescription)")
                let userInfo = ["error": error.localizedDescription]
                NSNotificationCenter.defaultCenter().postNotificationName(
                  Constants.NotificationKeys.Registration, object: nil, userInfo: userInfo)
              } else {
                AppState.sharedInstance.subscribed = true
              }
      })
    }
  }

  func registerToFriendlyPing() {
    if !AppState.sharedInstance.registeredToFP {
      var profilePictureUrl: String
      if let
        user = GIDSignIn.sharedInstance().currentUser,
        userProfile = user.profile {
          if userProfile.hasImage {
            profilePictureUrl = userProfile.imageURLWithDimension(50).absoluteString!
          } else {
            profilePictureUrl = "default"
          }
          let data = ["action": "register_new_client", "name": userProfile.name,
            "registration_token": AppState.sharedInstance.registrationToken!,
            "profile_picture_url": profilePictureUrl]
          var messageId = NSProcessInfo.processInfo().globallyUniqueString
          GCMService.sharedInstance().sendMessage(data, to: AppState.sharedInstance.serverAddress,
            withId: messageId)
          AppState.sharedInstance.registeredToFP = true
      } else {
        println("User profile is not available")
      }
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