package expo.modules.salesforcemiaw

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.salesforce.android.smi.common.api.Result
import com.salesforce.android.smi.core.CoreClient
import com.salesforce.android.smi.core.CoreConfiguration
import com.salesforce.android.smi.core.events.CoreEvent
import com.salesforce.android.smi.network.data.domain.conversationEntry.entryPayload.EntryPayload
import com.salesforce.android.smi.ui.UIClient
import com.salesforce.android.smi.ui.UIConfiguration
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.*
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import com.salesforce.android.smi.network.internal.dto.response.remoteconfig.ConversationOptionsConfiguration
import com.salesforce.android.smi.network.internal.dto.response.remoteconfig.TranscriptConfiguration
import com.salesforce.android.smi.network.internal.dto.response.businesshours.BusinessHoursInfo

class ExpoSalesForceMIAWModule : Module() {
  private var coreClient: CoreClient? = null
  private var coreConfiguration: CoreConfiguration? = null
  private var currentConversationId: UUID? = null
  private var userCanEditPreChatFields: Boolean = false
  private var finalizeSessionOnClose: Boolean = false

  private val preChatFieldsMap = mutableMapOf<String, String>()
  private val hiddenFieldsMap = mutableMapOf<String, String>()

  private val moduleScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
  private var activityLifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null
  private val messagingActivityRef = AtomicReference<Activity?>(null)

  private val TAG = "ExpoSalesForceMIAW"

  override fun definition() = ModuleDefinition {
    Name("ExpoSalesForceMIAW")

    Events("onChatOpened", "onChatClosed", "onTyping", "onQueueUpdate")

    OnCreate {
      setupLifecycleTracker()
    }

    OnDestroy { cleanup() }

    AsyncFunction("getBusinessHoursStatus") { promise: Promise ->
      val client = coreClient
      if (client == null) {
        promise.reject("ERR_NOT_CONFIGURED", "CoreClient não inicializado", null)
        return@AsyncFunction
      }
      moduleScope.launch {
        try {
          val result = client.retrieveBusinessHours()
          val businessHoursInfo: BusinessHoursInfo? = (result as? Result.Success)?.data
          if (businessHoursInfo != null) {
            promise.resolve(
              mapOf(
                "isWithinBusinessHours" to businessHoursInfo.isWithinBusinessHours(),
                "name" to businessHoursInfo.name,
                "isActive" to businessHoursInfo.isActive,
                "requestTimestamp" to businessHoursInfo.requestTimestamp
              )
            )
          } else {
            promise.reject("ERR_FETCH", "Não foi possível recuperar horários", null)
          }
        } catch (e: Exception) {
          promise.reject("ERR_EXCEPTION", e.message, null)
        }
      }
    }

    AsyncFunction("resetChatSession") { promise: Promise ->
      moduleScope.launch {
        internalCloseSync()
        promise.resolve(true)
      }
    }

    AsyncFunction("closeChat") { promise: Promise ->
      moduleScope.launch {
        try {
          internalCloseSync()
          sendEvent("onChatClosed", mapOf("reason" to "manual_close"))
          promise.resolve(true)
        } catch (e: Exception) {
          promise.reject("ERR_CLOSE", e.message, null)
        }
      }
    }

    Function("configure") { config: Map<String, Any?> ->
      try {
        val serviceUrl = config["url"] as? String ?: ""
        val orgId = config["orgId"] as? String ?: ""
        val devName = config["developerName"] as? String ?: ""

        if (serviceUrl.isEmpty() || orgId.isEmpty()) return@Function null

        val convIdStr = config["conversationId"] as? String
        currentConversationId =
          if (!convIdStr.isNullOrEmpty()) UUID.fromString(convIdStr) else UUID.randomUUID()

        coreConfiguration = CoreConfiguration(URL(serviceUrl), orgId, devName)

        userCanEditPreChatFields = config["userCanEditPreChatFields"] as? Boolean ?: false
        finalizeSessionOnClose = config["finalizeSessionOnClose"] as? Boolean ?: false

        preChatFieldsMap.clear()
        (config["preChatFields"] as? Map<*, *>)?.forEach { (k, v) ->
          preChatFieldsMap[k.toString()] = v.toString()
        }

        hiddenFieldsMap.clear()
        (config["hiddenPreChatFields"] as? Map<*, *>)?.forEach { (k, v) ->
          hiddenFieldsMap[k.toString()] = v.toString()
        }

        currentConversationId?.toString()
      } catch (e: Exception) {
        Log.e(TAG, "Erro configure: ${e.message}")
        null
      }
    }

    AsyncFunction("openChat") { promise: Promise ->
      whenChatReady(promise) { client, config ->
        val activity = appContext.currentActivity ?: return@whenChatReady promise.reject(
          "ERR_NO_ACTIVITY",
          "Activity não disponível",
          null
        )
        val conversationId = currentConversationId ?: UUID.randomUUID()
        val conversationClient = client.conversationClient(conversationId)

        moduleScope.launch {
          val result = client.retrieveRemoteConfiguration()
          if (result is Result.Success) {
            val remoteConfig = result.data
            remoteConfig.forms.firstOrNull()?.formFields?.forEach { field ->
              preChatFieldsMap[field.name]?.let { field.userInput = it }
            }
            conversationClient.submitRemoteConfiguration(remoteConfig)

            withContext(Dispatchers.Main) {
              val uiConfig = UIConfiguration(
                config, conversationId,
                transcriptConfiguration = TranscriptConfiguration(allowTranscriptDownload = true),
                conversationOptionsConfiguration = ConversationOptionsConfiguration(allowEndChat = true),
              )
              val uiClient = UIClient.Factory.create(uiConfig)
              uiClient.openConversationActivity(activity)
              sendEvent("onChatOpened", mapOf("conversationId" to conversationId.toString()))
              promise.resolve(true)
            }
          }
        }
      }
    }
  }

  private suspend fun handleCoreEvent(event: CoreEvent) {
    Log.i(TAG, "handleCoreEvent: ${event}")
    if (event is CoreEvent.ConversationEvent.Entry) {
      val payload = event.conversationEntry.payload
      when (payload) {
        is EntryPayload.CloseConversationPayload -> {
          Log.i(TAG, "EntryPayload.CloseConversationPayload")
          finalizeSession("session_ended")
        }
        is EntryPayload.SessionStatusChangedPayload -> {
          val status = payload.sessionStatus.toString()
          Log.i(TAG, "EntryPayload.SessionStatusChangedPayload status: ${status}")
          if (status.contains("Closed", true) || status.contains("Ended", true)) {
            finalizeSession("session_terminated")
          }
        }
        is EntryPayload.QueuePositionPayload -> sendEvent("onQueueUpdate", mapOf("position" to payload.position))
        is EntryPayload.TypingStartedIndicatorPayload -> sendEvent("onTyping", mapOf("isTyping" to true))
        is EntryPayload.TypingStoppedIndicatorPayload -> sendEvent("onTyping", mapOf("isTyping" to false))
        else -> Unit
      }
    }
  }

  private fun whenChatReady(promise: Promise?, callback: (client: CoreClient, config: CoreConfiguration) -> Unit) {
    Log.i(TAG, "whenChatReady")
    val config = coreConfiguration ?: return
    val context = appContext.reactContext ?: return
    val client = coreClient ?: CoreClient.Factory.create(context, config).also {
      coreClient = it
      it.start(moduleScope)
    }
    callback(client, config)
  }

  private fun finalizeSession(reason: String) {
    Log.i(TAG, "finalizeSession: ${reason}")
    moduleScope.launch(Dispatchers.Main) {
      sendEvent("onChatClosed", mapOf("reason" to reason))
      internalCloseSync()
      messagingActivityRef.getAndSet(null)?.finish()
    }
  }

  private suspend fun internalCloseSync() {
    Log.i(TAG, "internalCloseSync")
    withContext(Dispatchers.IO) {
      try {
        currentConversationId?.let { id -> coreClient?.conversationClient(id)?.closeConversation() }
        coreClient?.stop()
        appContext.reactContext?.let { CoreClient.clearStorage(it) }
        coreClient = null
        currentConversationId = null
      } catch (e: Exception) { Log.e(TAG, "Erro close: ${e.message}") }
    }
  }

  private fun setupLifecycleTracker() {
    Log.i(TAG, "setupLifecycleTracker")
    val app = appContext.currentActivity?.application ?: return
    activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
      override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        Log.i(TAG, "setupLifecycleTracker onActivityCreated")
        if (activity.localClassName.contains("MessagingInappActivity", true)) messagingActivityRef.set(activity)
      }
      override fun onActivityDestroyed(activity: Activity) {
        Log.i(TAG, "setupLifecycleTracker onActivityDestroyed")
        if (activity.localClassName.contains("MessagingInappActivity", true)) {
          messagingActivityRef.compareAndSet(activity, null)
          sendEvent("onChatClosed", mapOf("reason" to "user_manually_closed"))
        }
      }
      override fun onActivityStarted(a: Activity) {}
      override fun onActivityResumed(a: Activity) {}
      override fun onActivityPaused(a: Activity) {}
      override fun onActivityStopped(a: Activity) {}
      override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
    }.also { app.registerActivityLifecycleCallbacks(it) }
  }
  private fun cleanup() {
    activityLifecycleCallbacks?.let { appContext.currentActivity?.application?.unregisterActivityLifecycleCallbacks(it) }
    moduleScope.cancel()
  }
}
