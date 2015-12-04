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

/** Keeps track of the application state */
class AppState: NSObject {

  static let sharedInstance = AppState()

  /** True if app is connected to GCM */
  var connectedToGcm = false
  /** True if app is subscribed to new clients topic */
  var subscribed = false
  /** GCM registration token of the app */
  var registrationToken: String?
  /** senderID of the Friendly Ping server */
  var senderID: String?
  /** GCM address of the Friendly Ping server */
  var serverAddress: String?
  /** True if the client is registered to the Friendly Ping server */
  var registeredToFP = false
  /** True if the user has signed in */
  var signedIn = false

}
