export interface SalesForceMIAWConfig {
  url: string;
  orgId: string;
  developerName: string;
  conversationId?: string;
  preChatFields?: Record<string, string>;
  hiddenPreChatFields?: Record<string, string>;
  userCanEditPreChatFields?: boolean;
}

export type ExpoSalesForceMIAWModuleEvents = {
  openChat(): void;
  configure(config: SalesForceMIAWConfig): Promise<string | null | undefined>;
  closeChat(): void;
  setPreChatFields(fields: Record<string, string>): Promise<boolean>;
  setHiddenPreChatFields(fields: Record<string, string>): Promise<boolean>;
  registerPushToken(token: string): Promise<boolean>;
  getConversationId(): Promise<string | null>;
  clearConversationId(): Promise<string>;
};
