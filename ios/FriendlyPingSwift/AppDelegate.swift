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

  /** Load configuration data from the GoogleService-Info.plist file */
  func configureGGLContext() {
    var configureError:NSError?
    GGLContext.sharedInstance().configureWithError(&configureError)
    if configureError != nil {
      print("Error configuring the Google context: \(configureError)")
    }
    GIDSignIn.sharedInstance().delegate = self
    let senderID = GGLContext.sharedInstance().configuration.gcmSenderID
    AppState.sharedInstance.senderID = senderID
    AppState.sharedInstance.serverAddress = "\(senderID)@gcm.googleapis.com"
  }

  /** Initialize configuration of GCM */
  func configureGCMService() {
    let config = GCMConfig.defaultConfig()
    config.receiverDelegate = self
    config.logLevel = GCMLogLevel.Debug
    GCMService.sharedInstance().startWithConfig(config)
  }

  /** Register for remote notifications to get an APNs token to use for registration to GCM */
  func registerForRemoteNotifications(application: UIApplication) {
    if #available(iOS 8.0, *) {
      let settings: UIUserNotificationSettings =
      UIUserNotificationSettings(forTypes: [.Alert, .Badge, .Sound], categories: nil)
      application.registerUserNotificationSettings(settings)
      application.registerForRemoteNotifications()
    } else {
      // Fallback
      let types: UIRemoteNotificationType = [.Alert, .Badge, .Sound]
      application.registerForRemoteNotificationTypes(types)
    }
  }

  /** Handle the URL that the application receives at the end of the authentication process */
  func application(application: UIApplication,
      openURL url: NSURL, sourceApplication: String?, annotation: AnyObject) -> Bool {
    return GIDSignIn.sharedInstance().handleURL(url, sourceApplication: sourceApplication,
        annotation: annotation)
  }

  /** Sign the user in to FriendlyPing */
  func signIn(signIn: GIDSignIn!, didSignInForUser user: GIDGoogleUser!,
      withError error: NSError!) {
    if (error == nil) {
      AnalyticsHelper.sendLoginEvent()
      AppState.sharedInstance.signedIn = true
      connectToFriendlyPing()
      NSNotificationCenter.defaultCenter().postNotificationName(Constants.NotificationKeys.SignedIn,
        object: nil, userInfo: nil)
    } else {
      print("\(error.localizedDescription)")
    }
  }

  /** Sign the user out of FriendlyPing */
  func signIn(signIn: GIDSignIn!, didDisconnectWithUser user:GIDGoogleUser!,
      withError error: NSError!) {
    AppState.sharedInstance.signedIn = false
    AppState.sharedInstance.registeredToFP = false
  }

  // Connect to GCM when application becomes active
  func applicationDidBecomeActive( application: UIApplication) {
    GCMService.sharedInstance().connectWithHandler({
      (NSError error) -> Void in
      if error != nil {
        print("Could not connect to GCM: \(error.localizedDescription)")
      } else {
        print("Connected to GCM")
        AppState.sharedInstance.connectedToGcm = true
        self.connectToFriendlyPing()
      }
    })
  }

  /** Tear down connection to GCM when application goes in background */
  func applicationDidEnterBackground(application: UIApplication) {
    GCMService.sharedInstance().disconnect()
    AppState.sharedInstance.connectedToGcm = false
  }

  /** Use APNs token to get a registration token for GCM */
  func application( application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken
      deviceToken: NSData ) {
    GGLInstanceID.sharedInstance().startWithConfig(GGLInstanceIDConfig.defaultConfig())
    let registrationOptions = [kGGLInstanceIDRegisterAPNSOption:deviceToken,
        // This should be set to false if your certificate is for the APNs production environment
        kGGLInstanceIDAPNSServerTypeSandboxOption:true]
    GGLInstanceID.sharedInstance().tokenWithAuthorizedEntity(AppState.sharedInstance.senderID,
        scope: kGGLInstanceIDScopeGCM, options: registrationOptions, handler: registrationHandler)
  }

  /** There has been an error registering for remote notification */
  func application( application: UIApplication, didFailToRegisterForRemoteNotificationsWithError
      error: NSError ) {
    print("Registration for remote notification failed with error: \(error.localizedDescription)")
    let userInfo = ["error": error.localizedDescription]
    NSNotificationCenter.defaultCenter().postNotificationName(
        Constants.NotificationKeys.Registration, object: nil, userInfo: userInfo)
  }

  /** Handles the completion of registration with GCM */
  func registrationHandler(registrationToken: String!, error: NSError!) {
    if (registrationToken != nil) {
      AppState.sharedInstance.registrationToken = registrationToken;
      connectToFriendlyPing()
      let userInfo = ["registrationToken": registrationToken]
      NSNotificationCenter.defaultCenter().postNotificationName(
        Constants.NotificationKeys.Registration, object: nil, userInfo: userInfo)
    } else {
      print("Registration to GCM failed with error: \(error.localizedDescription)")
      let userInfo = ["error": error.localizedDescription]
      NSNotificationCenter.defaultCenter().postNotificationName(
        Constants.NotificationKeys.Registration, object: nil, userInfo: userInfo)
    }
  }

  // Handles reception of a notification */
  func application( application: UIApplication,
      didReceiveRemoteNotification userInfo: [NSObject : AnyObject]) {
    GCMService.sharedInstance().appDidReceiveMessage(userInfo);
    NSNotificationCenter.defaultCenter().postNotificationName(
        Constants.NotificationKeys.Message, object: nil, userInfo: userInfo)
  }

  // Handles reception of a notification */
  func application( application: UIApplication,
      didReceiveRemoteNotification userInfo: [NSObject : AnyObject],
      fetchCompletionHandler handler: (UIBackgroundFetchResult) -> Void) {
    GCMService.sharedInstance().appDidReceiveMessage(userInfo);
    NSNotificationCenter.defaultCenter().postNotificationName(
        Constants.NotificationKeys.Message, object: nil, userInfo: userInfo)
    handler(UIBackgroundFetchResult.NoData);
  }

  /** Connects to the FriendlyPing server */
  func connectToFriendlyPing() {
    if AppState.sharedInstance.connectedToGcm && AppState.sharedInstance.signedIn &&
        AppState.sharedInstance.registrationToken != nil {
      subscribeToTopic()
      registerToFriendlyPing()
    }
  }

  /** Subscribes to the topic used for announcement of new clients */
  func subscribeToTopic() {
    if !AppState.sharedInstance.subscribed {
      GCMPubSub().subscribeWithToken(AppState.sharedInstance.registrationToken!,
          topic: Constants.GCMStrings.Topic, options: nil, handler: {
            (NSError error) -> Void in
              if (error != nil) {
                // TODO(silvano): treat already subscribed with more grace
                print("Topic subscription failed with error: \(error.localizedDescription)")
                let userInfo = ["error": error.localizedDescription]
                NSNotificationCenter.defaultCenter().postNotificationName(
                  Constants.NotificationKeys.Registration, object: nil, userInfo: userInfo)
              } else {
                AppState.sharedInstance.subscribed = true
              }
      })
    }
  }

  /** Adds the local client to the registered clients of FriendlyPing */
  func registerToFriendlyPing() {
    if !AppState.sharedInstance.registeredToFP {
      var profilePictureUrl: String
      if let
        user = GIDSignIn.sharedInstance().currentUser,
        userProfile = user.profile {
          if userProfile.hasImage {
            profilePictureUrl = userProfile.imageURLWithDimension(50).absoluteString
          } else {
            profilePictureUrl = "default"
          }
          let data = ["action": "register_new_client", "name": userProfile.name,
            "registration_token": AppState.sharedInstance.registrationToken!,
            "profile_picture_url": profilePictureUrl]
          let messageId = NSProcessInfo.processInfo().globallyUniqueString
          GCMService.sharedInstance().sendMessage(data, to: AppState.sharedInstance.serverAddress,
            withId: messageId)
          AppState.sharedInstance.registeredToFP = true
      } else {
        print("User profile is not available")
      }
    }
  }
}