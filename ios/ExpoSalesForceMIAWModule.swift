import ExpoModulesCore
import SwiftUI
import SMIClientCore
import SMIClientUI
import UIKit

public class ExpoSalesForceMIAWModule: Module {
    private var uiConfiguration: UIConfiguration?
    private var coreDelegateAdapter: SalesForceMIAWDelegateAdapter?
    private var coreClient: CoreClient?
    private var currentConversationId: UUID?
    private var userCanEditPreChatFields: Bool = false
    private var preChatData: [String: String] = [:]
    private var hiddenPreChatData: [String: String] = [:]
    private var dataKey: String = "ExpoSalesForceMIAW_ConversationId"
  
  public func definition() -> ModuleDefinition {
    Name("ExpoSalesForceMIAW")
    
      OnCreate {
          self.currentConversationId = nil
          self.uiConfiguration = nil
          self.preChatData = [:]
          self.hiddenPreChatData = [:]
          self.dataKey = "ExpoSalesForceMIAW_ConversationId"
      }
      
      Events("onChatOpened", "onChatClosed")
      
      Function("closeChat") {
          self.closeChatFunc()
      }
      
      Function("configure") { (config: [String: Any]) -> String? in

          print("DEBUG: ✅ [ExpoSalesForceMIAW] SDK Configured with: \(config)")
          guard let urlString = config["url"] as? String,
                let orgId = config["orgId"] as? String,
                let developerName = config["developerName"] as? String else {
                  return nil
                }

          
          let convId = (config["conversationId"] as? String).flatMap(UUID.init) ?? self.getOrCreateConversationId()
          self.currentConversationId = convId
          self.userCanEditPreChatFields = config["userCanEditPreChatFields"] as? Bool ?? false
          
          self.uiConfiguration = UIConfiguration(
              serviceAPI: URL(string: urlString)!,
              organizationId: orgId,
              developerName: developerName,
              conversationId: convId
          )
          
          if let preChatFields = config["preChatFields"] as? [String: String] {
              self.preChatData = preChatFields
          }
          
          if let hiddenFields = config["hiddenPreChatFields"] as? [String: String] {
              self.hiddenPreChatData = hiddenFields
          }
          
          return convId.uuidString
      }
      
      AsyncFunction("openChat") { (promise: Promise) in
          DispatchQueue.main.async {
              guard let config = self.uiConfiguration else {
                  promise.reject("ERR_NOT_CONFIGURED", "SDK not configured.")
                  return
              }
          
              guard let rootViewController = self.getRootViewController() else {
                  promise.reject("ERR_NO_ROOT_VC", "Could not find root view controller.")
                  return
              }
              
     
              let coreClient = CoreFactory.create(withConfig: config)
              self.coreClient = coreClient
              
              let adapter = SalesForceMIAWDelegateAdapter(hiddenData: self.hiddenPreChatData, module: self)
              self.coreDelegateAdapter = adapter
              
              coreClient.preChatDelegate = adapter
              coreClient.addDelegate(delegate: adapter)
              
              coreClient.start()
              
              coreClient.retrieveRemoteConfiguration(completion: { [weak self] remoteConfig, error in
                  guard let self = self, let configRemote = remoteConfig else {
                      if let error = error { print("❌ Error Remote Config: \(error.localizedDescription)") }
                      return
                  }
                  self.submitRemoteConfig(coreClient: coreClient, remoteConfig: configRemote)
              })

              let chatView: ModalInterfaceViewController
              if !self.preChatData.isEmpty {
                  chatView = ModalInterfaceViewController(config, preChatFieldValueProvider: { [weak self] fields in
                      guard let self = self else { return fields }
                      return await self.modifyPreChatFields(preChatFields: fields)
                  })
              } else {
                  chatView = ModalInterfaceViewController(config)
              }
              
              rootViewController.present(chatView, animated: true) {
                  promise.resolve(true)
              }
          }
      }
  }
  
    private func submitRemoteConfig(coreClient: CoreClient, remoteConfig: RemoteConfiguration) {
        guard let conversationId = self.currentConversationId else { return }
            
            if let fields = remoteConfig.preChatConfiguration?.first?.preChatFields {
                for preChatField in fields {
                    
                    if let value = self.preChatData[preChatField.name] ?? self.hiddenPreChatData[preChatField.name] {
                        preChatField.value = value
                    } else {
                        print("⚠️ Campo \(preChatField.name) não encontrado no App (ficará null)")
                    }
                }
            }

            let conversationClient = coreClient.conversationClient(with: conversationId)
  
            conversationClient.submit(remoteConfig: remoteConfig, createConversationOnSubmit: false)
    }

    private func getRootViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes
        let windowScene = scenes.first as? UIWindowScene
        var topController = windowScene?.windows.first(where: { $0.isKeyWindow })?.rootViewController
        while let presented = topController?.presentedViewController { topController = presented }
        return topController
    }

    private func modifyPreChatFields(preChatFields: [PreChatField]) async -> [PreChatField] {
        let modifiedFields = preChatFields
        for (index, field) in modifiedFields.enumerated() {
            if let value = preChatData[field.name] {
                modifiedFields[index].value = value
                modifiedFields[index].isEditable = self.userCanEditPreChatFields
            }
        }
        return modifiedFields
    }

    private func cleanSession() {
        if let core = self.coreClient {
            if let adapter = self.coreDelegateAdapter {
                core.removeDelegate(delegate: adapter)
            }
            core.stop()
        }
        
  
        self.coreClient = nil
        self.coreDelegateAdapter = nil
        self.currentConversationId = nil
    }

    private func deleteConversationId() {
        UserDefaults.standard.removeObject(forKey: self.dataKey)
        self.currentConversationId = nil
        print("Conversation ID cleaned")
    }

     private func getOrCreateConversationId() -> UUID {
         if let id = UserDefaults.standard.string(forKey: self.dataKey), let uuid = UUID(uuidString: id) { return uuid }
         let newId = UUID()
//         self.saveConversationId(newId)
         return newId
     }
    
    private func saveConversationId() {
        if self.currentConversationId != nil {
            UserDefaults.standard.set(self.currentConversationId?.uuidString, forKey: self.dataKey)
            print("Conversation ID saved!")
        }
    }
    
    @objc func saveConversationData() {
        self.saveConversationId()
    }
    
    @objc func resetConversationData() {
        self.deleteConversationId()
    }
    
    @objc func closeChatFunc() {
        print("### closeChatFunc module ####")
        DispatchQueue.main.async {
            print("DEBUG: [ExpoSalesForceMIAW] Iniciando fechamento forçado...")
            
            let rootViewController = self.getRootViewController()
            if let core = self.coreClient, let convId = self.currentConversationId {
                
                core.closeConversation(withIdentifier: convId) { _ in }
            }
                
            self.cleanSession()
      
            rootViewController?.dismiss(animated: true) {
                print("DEBUG: ✅ [UI] Chat fechado e memória limpa.")
                self.sendEvent("onChatClosed", ["reason": "session_terminated"])
            }
        }
    }
}
