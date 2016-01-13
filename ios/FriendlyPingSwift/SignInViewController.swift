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

/** View controller for the sign in screen */
class SignInViewController: UIViewController, GIDSignInUIDelegate {

  @IBOutlet weak var signInButton: GIDSignInButton!

  /** Set SignIn delegate and try to sign in silently */
  override func viewDidLoad() {
    super.viewDidLoad()
    GIDSignIn.sharedInstance().uiDelegate = self
    GIDSignIn.sharedInstance().signInSilently()
    NSNotificationCenter.defaultCenter().addObserver(self, selector: "transitionIfReady",
      name: Constants.NotificationKeys.SignedIn, object: nil)
  }

  /** The user tapped the sign in button */
  @IBAction func didTapSignIn(sender: UIButton) {
    GIDSignIn.sharedInstance().signIn()
  }

  func signIn(signIn: GIDSignIn!, dismissViewController viewController: UIViewController!) {
    self.dismissViewControllerAnimated(true) { () -> Void in
      self.transitionIfReady()
    }
  }

  /** Perform segue to main screen after the user has signed in */
  func transitionIfReady() {
    if (GIDSignIn.sharedInstance().currentUser != nil && self.presentedViewController == nil) {
      self.performSegueWithIdentifier(Constants.Segues.SignInToFp, sender: nil)
    }
  }

}
