# expo-salesforce-miaw-local

This module provides a native integration for the Salesforce Messaging In-App and Web (MIAW) SDK for React Native applications, with full support for Expo (SDK 53+). [Salesforce Messaging In-App and Web (MIAW)](https://developer.salesforce.com/docs/service/messaging-in-app/guide/overview.html)

It allows you to easily add Salesforce chat functionality to your app, leveraging native iOS and Android SDKs for the best performance and user experience.

##Installation

To install the module, download the release zip, and extract it on your project folder. 

##Configuration

### 1. App Rebuild

After installation and configuration, you need to rebuild your application for the native changes to be applied:

```bash
npx expo prebuild --clean
npx expo run:ios
npx expo run:android
```

## API

### configure(config)

Configures the SDK manually. Returns a `Promise<boolean>`.

```javascript
import SalesForceMIAW from "@/modules/expo-salesforce-miaw-local";

const config = {
  url: "YOUR_SALESFORCE_URL",
  orgId: "YOUR_ORG_ID",
  developerName: "YOUR_DEPLOYMENT_NAME",
  //Optional:
  preChatFields: {
    FirstName: "Test",
    LastName: "User",
  },
  //Optional:
  hiddenPreChatFields: {
    Segment: "VIP",
  },
};

const conversationID = SalesForceMIAW.configure(config);
if (conversationID) {
    console.log('SalesForceMiaw configured with conversationID: ', conversationID);
}
```

### openChat()

Opens the chat interface. Returns a `Promise<boolean>`.

```javascript
import SalesForceMIAW from "@/modules/expo-salesforce-miaw-local";

SalesForceMIAW.openChat();
```

### closeChat()

Closes the chat interface. Returns a Promise<boolean>.

```javascript
import SalesForceMIAW from "@/modules/expo-salesforce-miaw-local";

SalesForceMIAW.closeChat();
```

Licença (License)
MIT
