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

/** DataSource for the clients table */
class ClientListDataSource: NSObject, UITableViewDataSource {

  /** The registered clients */
  var clients : Array<FriendlyPingClient> = []

  /** Add a client */
  func addClient(c:FriendlyPingClient) {
    clients.append(c)
  }

  /** Move a client to the top of the table */
  func moveToTop(index:Int) {
    let newTopObject = clients[index]
    self.clients.removeAtIndex(index)
    self.clients.insert(newTopObject, atIndex: 0)
  }

  /** One section in the table */
  func numberOfSectionsInTableView (tableView: UITableView)
    -> Int {
      return 1
  }

  /** Number of rows in the table == number of clients */
  func tableView(_tableView: UITableView,
    numberOfRowsInSection section: Int) -> Int {
      return self.clients.count
  }

  /** Cells for clients */
  func tableView (_tableView: UITableView,
    cellForRowAtIndexPath indexPath: NSIndexPath) -> UITableViewCell {
      let cell = _tableView.dequeueReusableCellWithIdentifier("ClientCell")
        as UITableViewCell!
      let client = clients[indexPath.row]
      cell.textLabel?.text = client.name
      if let imageData = NSData (contentsOfURL: client.profilePictureUrl!) {
        cell.imageView!.image = UIImage (data: imageData)
      }
      return cell
  }

}

/** Extend sequence to iterate the list */
extension ClientListDataSource : SequenceType {
  typealias Generator = AnyGenerator<FriendlyPingClient>

  func generate() -> Generator {
    var index = 0
    return anyGenerator {
      if index < self.clients.count {
        return self.clients[index++]
      }
      return nil
    }
  }
}

/** Extend collection for array like operations */
extension ClientListDataSource : CollectionType {
  typealias Index = Int
  var startIndex: Int {
    return 0
  }
  var endIndex: Int {
    return clients.count
  }

  subscript(i: Int) -> FriendlyPingClient {
    return clients[i]
  }
}
