import { NativeModule, requireNativeModule } from "expo";

import {
  ExpoSalesForceMIAWModuleEvents,
  SalesForceMIAWConfig,
} from "./ExpoSalesForceMIAW.types";

declare class ExpoSalesForceMIAWMod extends NativeModule<ExpoSalesForceMIAWModuleEvents> {
  openChat(): void;
  configure(config: SalesForceMIAWConfig): Promise<string | null | undefined>;
  closeChat(): void;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ExpoSalesForceMIAWMod>("ExpoSalesForceMIAW");
