# Project status #
![status: inactive](https://img.shields.io/badge/status-inactive-red.svg)

This project is no longer actively maintained, and remains here as an archive of this work.

# Friendly Ping

Friendly Ping demonstrates using GCM, Analytics, Sign In and AdMob together in a single application. 

You can use this as a reference for how to implement your own basic application.

## Getting started

Clone the code of the project:

    git clone https://github.com/googlesamples/friendlyPing.git

To get started, you need to enable Google services for your app for at least one of the two 
available clients.

### Android

Get a configuration file for your android app opening the following link in a new tab:
[https://developers.google.com/mobile/add](https://developers.google.com/mobile/add)

Use `com.google.samples.apps.friendlyping` as package name when creating your project, and enable
the following APIs:

- Google Sign-In
- Cloud Messaging
- Analytics
- AdMob

Download the google-services.json file into `friendlyPing/android/app/`
If you want to check out the code, open Android Studio, select **File > Open**, browse to where you 
cloned the friendlyPing repository, and open friendlyPing/android (if you are in the Android Studio
start screen, you can select **Open an existing Android Studio project** and use this same path).
Don't build and run the client until you have one of the server running.

Note: if Android Studio cannot resolve `@string/test_banner_ad_unit_id`, it should not be a problem
and you should still be able to build and run the app.

### iOS

Make sure you have [CocoaPods](https://developers.google.com/ios/cocoapods).

Open a terminal window and navigate to `friendlyPing/ios`, then run:

    pod install

This creates a `FriendlyPing.xcworkspace` file in the folder. Run the following command to open the
project in xcode:

    open FriendlyPing.xcworkspace

Get a configuration file for your iOS app opening the following link in a new tab:
[https://developers.google.com/mobile/add](https://developers.google.com/mobile/add)

To configure the project, you need to provide a valid APNs certificate and some additional information
to get a configuration file and finish setting up your project. If you don't already have an APNs certificate
see [Provisioning APNs SSL Certificates](https://developers.google.com/cloud-messaging/ios/certs).
When prompted, provide the Bundle ID associated with your APNs certificate, and enable the following
APIs:

- Google Sign-In
- Cloud Messaging
- Analytics
- AdMob

Download the `GoogleService-Info.plist` file to your mac, then add it to the `FriendlyPingSwift` target
in the xcode project.
To configure Google Sign-In:

- In the **Project > Target > Info > URL Types** panel, create a new item and paste your 
`REVERSED_CLIENT_ID` into the URL Schemes field. You can find your `REVERSED_CLIENT_ID` in the `GoogleService-Info.plist` file.
- Also in the **Project > Target > Info > URL Types** panel, create a new item and type your bundle identifier
in the **URL Schemes** field.

Don't build and run the client until you have one of the servers running. Don't run 2 servers though :).

### Go server

Read the [Go server instructions](server/Go/README.md) to start up the Go server locally on your machine. 

### Java server

Read the [Java server instructions](server/Java/README.md) to start up the Java server locally on your machine. 

## How to make contributions?
Please read and follow the steps in the [CONTRIBUTING.md](CONTRIBUTING.md)

## License
See [LICENSE](LICENSE)
