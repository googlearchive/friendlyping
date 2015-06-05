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

@objc(FPViewController)
class FPViewController: UIViewController, UITableViewDelegate {

  @IBOutlet weak var banner: GADBannerView!
  @IBOutlet weak var clientTable: UITableView!
  @IBOutlet var clients: ClientListDataSource!
  
  enum Actions : String {
    case BroadcastNewClient = "broadcast_new_client"
    case SendClientList = "send_client_list"
    case PingClient = "ping_client"
  }

  override func viewDidLoad() {
    super.viewDidLoad()
    NSNotificationCenter.defaultCenter().addObserver(self, selector: "receiveMessage:",
      name:Constants.NotificationKeys.Message, object: nil)
    self.banner.adUnitID = GGLContext.sharedInstance().adUnitIDForBannerTest
    self.banner.rootViewController = self
    self.banner.loadRequest(GADRequest())
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
          if registrationToken != AppState.sharedInstance.registrationToken {
            var c = FriendlyPingClient(name:name, registrationToken:registrationToken,
              profilePictureUrl: NSURL(string: profilePictureUrl))
            self.clients.addClient(c)
            clientTable.reloadData()
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
                var c = FriendlyPingClient(name:name, registrationToken:registrationToken,
                    profilePictureUrl: NSURL(string: profilePictureUrl))
                self.clients.addClient(c)
            }
          }
          clientTable.reloadData()
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
            self.clients.moveToTop(index)
            clientTable.reloadData()
          }
        }
        showAlert(title, message: body)
      } else {
        println("Error decoding received ping")
      }
  }

  func tableView(tableView: UITableView, didSelectRowAtIndexPath indexPath: NSIndexPath) {
    var index = indexPath.row
    var c = clients[index]
    showAlert("Sending Ping!", message: "Pinging \(c.name!)")
    let data = ["action": "ping_client", "to": c.registrationToken!,
        "sender": AppState.sharedInstance.registrationToken!]
    var messageId = NSProcessInfo.processInfo().globallyUniqueString
    GCMService.sharedInstance().sendMessage(data, to: AppState.sharedInstance.serverAddress!,
        withId: messageId)
    GAI.sharedInstance().defaultTracker.send(GAIDictionaryBuilder.createEventWithCategory(
      "Ping", action: "Sent", label: nil, value: nil).build() as [NSObject:AnyObject]!)
    clients.moveToTop(index)
    clientTable.reloadData()
  }

  @IBAction func signOut(sender: UIButton) {
    GIDSignIn.sharedInstance().signOut()
    AppState.sharedInstance.registeredToFP = false
    performSegueWithIdentifier(Constants.Segues.FpToSignIn, sender: nil)

  }

  func showAlert(title:String, message:String) {
    dispatch_async(dispatch_get_main_queue()) {
      let alert = UIAlertController(title: title,
        message: message, preferredStyle: .Alert)
      let dismissAction = UIAlertAction(title: "Dismiss", style: .Destructive, handler: nil)
      alert.addAction(dismissAction)
      self.presentViewController(alert, animated: true, completion: nil)
    }
  }

  func guruMeditation() {
    let error = "Software failure. Guru meditation."
    showAlert("Error", message: error)
    println(error)
  }

}
