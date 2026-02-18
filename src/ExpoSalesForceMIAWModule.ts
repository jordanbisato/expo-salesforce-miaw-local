import { NativeModule, requireNativeModule } from "expo";

import {
  ExpoSalesForceMIAWModuleEvents,
  SalesForceMIAWConfig,
} from "./ExpoSalesForceMIAW.types";

declare class ExpoSalesForceMIAWMod extends NativeModule<ExpoSalesForceMIAWModuleEvents> {
  openChat(): void;
  configure(config: SalesForceMIAWConfig): Promise<string | null | undefined>;
  closeConversation(): void;
  setHiddenPreChatFields(fields: Record<string, string>): void;
  registerPushToken(token: string): void;
  getConversationId(): Promise<string | null>;
  clearConversationId(): void;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ExpoSalesForceMIAWMod>("ExpoSalesForceMIAW");
