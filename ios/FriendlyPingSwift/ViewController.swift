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

@objc(ViewController)
class ViewController: UIViewController {

  var clients : Array <FriendlyPingClient> = []
  var registrationToken : String?
  var serverAddress : String?

  enum Actions : String {
    case BroadcastNewClient = "broadcast_new_client"
    case SendClientList = "send_client_list"
    case PingClient = "ping_client"
  }

  override func viewDidLoad() {
    super.viewDidLoad()
    let appDelegate = UIApplication.sharedApplication().delegate as! AppDelegate
    NSNotificationCenter.defaultCenter().addObserver(self, selector: "updateRegistrationStatus:",
      name: appDelegate.registrationKey, object: nil)
    NSNotificationCenter.defaultCenter().addObserver(self, selector: "receiveMessage:",
      name: appDelegate.messageKey, object: nil)
  }

  func updateRegistrationStatus(notification: NSNotification) {
    // TODO(silvano): could this be a switch?
    if let info = notification.userInfo as? Dictionary<String,String> {
      if let error = info["error"] {
        showAlert("Error", message: error)
      } else if let registrationToken = info["registrationToken"] {
        self.registrationToken = registrationToken
        let appDelegate = UIApplication.sharedApplication().delegate as! AppDelegate
        self.serverAddress = "\(appDelegate.gcmSenderID!)@gcm.googleapis.com"
      } else {
        guruMeditation()
      }
    } else {
      guruMeditation()
    }
  }

  func receiveMessage(notification: NSNotification) {
    if let message = notification.userInfo as? Dictionary<String,AnyObject> {
      if let action = message["action"] as? String {
        switch action {
        case Actions.BroadcastNewClient.rawValue:
          didReceiveNewClient(message)
        case Actions.SendClientList.rawValue:
          didReceiveClientList(message)
        case Actions.PingClient.rawValue:
          didReceivePing(message)
        default:
          println("Action not supported: \(action)")
        }
      } else {
        println("Invalid message received: no action")
      }
    }
  }

  func didReceiveNewClient(userInfo: [NSObject: AnyObject]) {
    if let
      clientString = userInfo["client"] as? String,
      clientData:NSData = clientString.dataUsingEncoding(NSUTF8StringEncoding)
    {
      var jsonError: NSError?
      let client = NSJSONSerialization.JSONObjectWithData(clientData, options: nil,
          error: &jsonError) as! NSDictionary
      if jsonError != nil {
        println("Could not read new client: \(jsonError)")
      } else {
        if let
          name = client["name"] as? String,
          registrationToken = client["registration_token"] as? String,
          profilePictureUrl = client["profile_picture_url"] as? String
        {
          if registrationToken != self.registrationToken {
            var c = FriendlyPingClient(name:name, registrationToken:registrationToken,
              profilePictureUrl: NSURL(string: profilePictureUrl))
            self.clients.append(c)
          }
        }
      }
    } else {
      println("Invalid payload for new client action")
    }
  }

  func didReceiveClientList(userInfo: [NSObject: AnyObject]) {
    if let clientsString = userInfo["clients"] as? String {
      if let clientsData:NSData = clientsString.dataUsingEncoding(NSUTF8StringEncoding) {
        var jsonError: NSError?
        let clients = NSJSONSerialization.JSONObjectWithData(clientsData, options: nil,
          error: &jsonError) as! NSArray
        if jsonError != nil {
          println("Could not read client list: \(jsonError)")
        } else {
          for client in clients {
            if let
              name = client["name"] as? String,
              registrationToken = client["registration_token"] as? String,
              profilePictureUrl = client["profile_picture_url"] as? String
            {
              // don't add self to the clients list
              if registrationToken != self.registrationToken {
                var c = FriendlyPingClient(name:name, registrationToken:registrationToken,
                    profilePictureUrl: NSURL(string: profilePictureUrl))
                self.clients.append(c)
              }
              // TODO(silvano): remove the test ping when the UI lands
              if client["name"] as! String == "Larry" {
                let data = ["action": "ping_client", "to": self.serverAddress!, "sender": self.registrationToken!]
                var messageId = NSProcessInfo.processInfo().globallyUniqueString
                GCMService.sharedInstance().sendMessage(data, to: self.serverAddress!, withId: messageId)
              }
            }
          }
        }
      }
    }
  }

  func didReceivePing(message: [NSObject: AnyObject]) {
    if let
        aps = message["aps"] as? [String: AnyObject],
        alert = aps["alert"] as? [String: String]!,
        title = alert["title"] as String!,
        body  = alert["body"] as String!,
        sender = message["sender"] as? String
      {
        for (index, value) in enumerate(self.clients) {
          if value.registrationToken! == sender {
            var senderObject = self.clients[index]
            self.clients.removeAtIndex(index)
            self.clients.insert(senderObject, atIndex: 0)
          }
        }
        showAlert(title, message: body)
      } else {
        println("Error decoding received ping")
      }
  }

  // TODO(silvano) add addClient func to remove dup code
  func showAlert(title:String, message:String) {
    let alert = UIAlertController(title: title,
      message: message, preferredStyle: .Alert)
    let dismissAction = UIAlertAction(title: "Dismiss", style: .Destructive, handler: nil)
    alert.addAction(dismissAction)
    self.presentViewController(alert, animated: true, completion: nil)
  }

  func guruMeditation() {
    let error = "Software failure. Guru meditation."
    showAlert("Error", message: error)
    println(error)
  }

}
