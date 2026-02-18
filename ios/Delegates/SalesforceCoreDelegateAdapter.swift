import Foundation
import SMIClientCore
import SMIClientUI
import UIKit

class SalesForceMIAWDelegateAdapter: NSObject, CoreDelegate, HiddenPreChatDelegate {
    private let hiddenData: [String: String]
    private var conversationCreated: Bool = false
    weak var module: ExpoSalesForceMIAWModule?
    
    init(hiddenData: [String: String], module: ExpoSalesForceMIAWModule) {
        self.hiddenData = hiddenData
        self.module = module
        super.init()
    }

    func core(_ core: CoreClient,
              conversation: Conversation,
              didReceiveEntries entries: [ConversationEntry],
              paged: Bool) {
        
        module?.sendEvent("onMessageReceived", ["count": entries.count])
    }
    
    func core(_ core: CoreClient, conversation: Conversation, didUpdateActiveParticipants activeParticipants: [any Participant]) {
        print("ActiveParticipants changed: ", activeParticipants.count);
        if activeParticipants.count == 0 && self.conversationCreated {
            self.conversationCreated = false
            DispatchQueue.main.async {
                self.module?.resetConversationData()
            }
        } else if activeParticipants.count == 1 {
            self.module?.saveConversationData()
        }
    }
  
    func core(_ core: CoreClient, conversation: Conversation, didUpdateEntries entries: [ConversationEntry]) {
        print("🔄 Messages updated (delivery/read)")
    }

  
    func core(_ core: CoreClient, didCreateConversation conversation: Conversation) {
        print("✅ Conversa criada: \(conversation.identifier.uuidString)")
        self.conversationCreated = true
    }

  
    func core(_ core: CoreClient, didReceiveTypingStartedEvent event: ConversationEntry) {
        self.module?.sendEvent("onTyping", ["isTyping": true])
    }


    func core(_ core: CoreClient, didReceiveTypingStoppedEvent event: ConversationEntry) {
        self.module?.sendEvent("onTyping", ["isTyping": false])
    }

  
    func core(_ core: CoreClient, didChangeNetworkState state: NetworkConnectivityState) {
        print("🌐 Network state changed \(state)")
    }

 
    func core(_ core: CoreClient, didError error: Error) {
        print("❌ [Salesforce Error]: \(error.localizedDescription)")
    }


    func core(_ core: CoreClient,
              conversation: Conversation,
              didRequestPrechatValues hiddenPreChatFields: [HiddenPreChatField],
              completionHandler: @escaping HiddenPreChatValueCompletion) {
        
        let updatedFields = hiddenPreChatFields
        for (index, field) in updatedFields.enumerated() {
            if let value = hiddenData[field.name] {
                updatedFields[index].value = value
            }
        }
        completionHandler(updatedFields)
    }
}
