export interface SalesForceMIAWConfig {
  url: string;
  orgId: string;
  developerName: string;
  conversationId?: string;
  preChatFields?: Record<string, string>;
  hiddenPreChatFields?: Record<string, string>;
  userCanEditPreChatFields?: boolean;
  finalizeSessionOnClose?: boolean;
}

export type ExpoSalesForceMIAWModuleEvents = {
  openChat(): Promise<boolean>;
  configure(config: SalesForceMIAWConfig): Promise<string | null | undefined>;
  closeChat(): void;
};
