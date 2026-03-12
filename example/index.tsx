import SalesForceMIAWLocal from "@/modules/expo-salesforce-miaw-local";
import { Platform, Button, View } from "react-native";
import { useState } from "react";

export default function HomeScreen() {
  const [idChatSF, setIdChatSF] = useState(undefined);

  const handleOpenChat = async () => {
    const config = await SalesForceMIAWLocal.configure({
      url: "YOUR_SALESFORCE_URL",
      orgId: "YOUR_ORG_ID",
      developerName: "YOUR_DEPLOYMENT_NAME",
      userCanEditPreChatFields: false,
      conversationId: (Platform.OS === "android" && idChatSF) || undefined,
      preChatFields: {
        _firstName: "TEST",
        _lastName: "MIAW APP",
        _email: "teste@email.com",
      },
      hiddenPreChatFields: {
        SegmentHidden: "VIP",
      },
    });

    console.log("config: ", config);

    if (config) {
      setIdChatSF(config);
      await SalesForceMIAWLocal.openChat();
      console.log("Chat successfully opened");
    }
  };

  return (
    <View style={{ flex: 1, justifyContent: "center" }}>
      <Button title="Open CHAT" onPress={handleOpenChat} />
    </View>
  );
}
