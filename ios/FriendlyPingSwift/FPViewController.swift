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

/** View controller for the main app screen */
class FPViewController: UIViewController, UITableViewDelegate {

  /** AdMob banner */
  @IBOutlet weak var banner: GADBannerView!
  /** Table of connected clients */
  @IBOutlet weak var clientTable: UITableView!
  /** Data source for clients table */
  @IBOutlet var clients: ClientListDataSource!

  /** String identifiers for the type of messages implemented by the app */
  enum Actions : String {
    case BroadcastNewClient = "broadcast_new_client"
    case SendClientList = "send_client_list"
    case PingClient = "ping_client"
  }

  // Observe for reception of notifications and configure the AdMob banner */
  override func viewDidLoad() {
    super.viewDidLoad()
    NSNotificationCenter.defaultCenter().addObserver(self, selector: "receiveMessage:",
      name:Constants.NotificationKeys.Message, object: nil)
    self.banner.adUnitID = GGLContext.sharedInstance().adUnitIDForBannerTest
    self.banner.rootViewController = self
    self.banner.loadRequest(GADRequest())
  }

  /** Handler for reception of a message: check the message's action and dispatch the method */
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
          print("Action not supported: \(action)")
        }
      } else {
        print("Invalid message received: no action")
      }
    }
  }

  /** Add a new client to the table */
  func didReceiveNewClient(userInfo: [NSObject: AnyObject]) {
    if let
      clientString = userInfo["client"] as? String,
      clientData:NSData = clientString.dataUsingEncoding(NSUTF8StringEncoding)
    {
      do {
        let client = try NSJSONSerialization.JSONObjectWithData(clientData, options: [])
            as! NSDictionary
        if let
          name = client["name"] as? String,
          registrationToken = client["registration_token"] as? String,
          profilePictureUrl = client["profile_picture_url"] as? String
        {
          if registrationToken != AppState.sharedInstance.registrationToken {
            let c = FriendlyPingClient(name:name, registrationToken:registrationToken,
              profilePictureUrl: NSURL(string: profilePictureUrl))
            self.clients.addClient(c)
            clientTable.reloadData()
          }
        }
      } catch let jsonError as NSError {
        print("Could not read new client: \(jsonError)")
      }
    } else {
      print("Invalid payload for new client action")
    }

  }

  /** Receive list of connected clients */
  func didReceiveClientList(userInfo: [NSObject: AnyObject]) {
    if let clientsString = userInfo["clients"] as? String {
      if let clientsData:NSData = clientsString.dataUsingEncoding(NSUTF8StringEncoding) {
        do {
          let clients = try NSJSONSerialization.JSONObjectWithData(clientsData, options: [])
              as! NSArray
          for client in clients {
            if let
              name = client["name"] as? String,
              registrationToken = client["registration_token"] as? String,
              profilePictureUrl = client["profile_picture_url"] as? String
            {
              let c = FriendlyPingClient(name:name, registrationToken:registrationToken,
                profilePictureUrl: NSURL(string: profilePictureUrl))
              self.clients.addClient(c)
            }
          }
          clientTable.reloadData()
        } catch let jsonError as NSError {
          print("Could not read client list: \(jsonError)")
        }
      }
    }
  }

  /** Receive a ping from a client */
  func didReceivePing(message: [NSObject: AnyObject]) {
    if let
        aps = message["aps"] as? [String: AnyObject],
        alert = aps["alert"] as? [String: String]!,
        title = alert["title"] as String!,
        body  = alert["body"] as String!,
        sender = message["sender"] as? String
      {
        for (index, value) in self.clients.enumerate() {
          if value.registrationToken! == sender {
            self.clients.moveToTop(index)
            clientTable.reloadData()
          }
        }
        showAlert(title, message: body)
      } else {
        print("Error decoding received ping")
      }
  }

  /** Send a ping to the client selected on the table, moving the recipient to the top */
  func tableView(tableView: UITableView, didSelectRowAtIndexPath indexPath: NSIndexPath) {
    let index = indexPath.row
    let c = clients[index]
    showAlert("Sending Ping!", message: "Pinging \(c.name!)")
    let data = ["action": "ping_client", "to": c.registrationToken!,
        "sender": AppState.sharedInstance.registrationToken!]
    let messageId = NSProcessInfo.processInfo().globallyUniqueString
    GCMService.sharedInstance().sendMessage(data, to: AppState.sharedInstance.serverAddress!,
        withId: messageId)
    AnalyticsHelper.sendPingEvent()
    clients.moveToTop(index)
    clientTable.reloadData()
  }

  /** Sign the user out and go back to login screen */
  @IBAction func signOut(sender: UIButton) {
    GIDSignIn.sharedInstance().signOut()
    performSegueWithIdentifier(Constants.Segues.FpToSignIn, sender: nil)

  }

  /** Show alerts upon sending/receiving pings */
  func showAlert(title:String, message:String) {
    dispatch_async(dispatch_get_main_queue()) {
      if #available(iOS 8.0, *) {
        let alert = UIAlertController(title: title,
          message: message, preferredStyle: .Alert)
        let dismissAction = UIAlertAction(title: "Dismiss", style: .Destructive, handler: nil)
        alert.addAction(dismissAction)
        self.presentViewController(alert, animated: true, completion: nil)
      } else {
        // Fallback on earlier versions
        let alert = UIAlertView.init(title: title, message: message, delegate: nil,
          cancelButtonTitle: "Dismiss")
        alert.show()
      }
    }
  }

  /** An error that cannot be recovered */
  func guruMeditation() {
    let error = "Software failure. Guru meditation."
    showAlert("Error", message: error)
    print(error)
  }

}
