package expo.modules.salesforcemiaw

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
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

class ExpoSalesForceMIAWModule : Module() {
  private var coreClient: CoreClient? = null
  private var coreConfiguration: CoreConfiguration? = null
  private var currentConversationId: UUID? = null

  private val prefsName = "ExpoSalesforceMIAW_prefs"
  private val conversationIdKey = "ExpoSalesforceMIAW.conversationId"

  private val preChatFieldsMap = mutableMapOf<String, String>()
  private val hiddenFieldsMap = mutableMapOf<String, String>()

  private val moduleScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
  private var activityLifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null
  private val messagingActivityRef = AtomicReference<Activity?>(null)

  private fun prefs(): SharedPreferences =
    appContext.reactContext!!.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

  private val TAG = "ExpoSalesForceMIAW"

  override fun definition() = ModuleDefinition {
    Name("ExpoSalesForceMIAW")

    Events("onChatOpened", "onChatClosed", "onTyping", "onQueueUpdate")

    OnCreate { setupLifecycleTracker() }
    OnDestroy { cleanup() }

    AsyncFunction("resetChatSession") { promise: Promise ->
      moduleScope.launch {
        try {
          internalCloseSync()
          promise.resolve(true)
        } catch (e: Exception) {
          promise.reject("ERR_RESET", e.message, null)
        }
      }
    }

    AsyncFunction("closeChat") { promise: Promise ->
      moduleScope.launch {
        internalCloseSync()
        sendEvent("onChatClosed", mapOf("reason" to "manual_close"))
        promise.resolve(true)
      }
    }

    Function("configure") { config: Map<String, Any?> ->
      runCatching {
        val serviceUrl = config["url"] as? String ?: ""
        val orgId = config["orgId"] as? String ?: ""
        val devName = config["developerName"] as? String ?: ""

//        if (!serviceUrl.contains("salesforce-scrt.com")) return@Function false

        val convIdStr = config["conversationId"] as? String
        currentConversationId = convIdStr?.let { UUID.fromString(it) } ?: getOrCreateConversationId()

        coreConfiguration = CoreConfiguration(URL(serviceUrl), orgId, devName)

        preChatFieldsMap.clear()
        (config["preChatFields"] as? Map<*, *>)?.forEach { (k, v) ->
          preChatFieldsMap[k.toString()] = v.toString()
        }

        hiddenFieldsMap.clear()
        (config["hiddenPreChatFields"] as? Map<*, *>)?.forEach { (k, v) ->
          hiddenFieldsMap[k.toString()] = v.toString()
        }
        true
      }.getOrElse {
        Log.e(TAG, "Erro na configuração", it)
        false
      }
    }

    AsyncFunction("openChat") { promise: Promise ->
      whenChatReady(promise) { client, config ->
        val activity = appContext.currentActivity
          ?: return@whenChatReady promise.reject(
            "ERR_NO_ACTIVITY",
            "Activity não disponível",
            null
          )

        val conversationId = currentConversationId ?: UUID.randomUUID()
        val conversationClient = client.conversationClient(conversationId)

        moduleScope.launch {
          conversationClient.events.collect { event -> handleCoreEvent(event) }
        }

        moduleScope.launch {
          val result = client.retrieveRemoteConfiguration()
          if (result is Result.Success) {
            val remoteConfig = result.data
            remoteConfig.forms.firstOrNull()?.formFields?.forEach { field ->
              preChatFieldsMap[field.name]?.let { field.userInput = it }
            }
            conversationClient.submitRemoteConfiguration(remoteConfig)

            withContext(Dispatchers.Main) {
              val uiConfig = UIConfiguration(config, conversationId)
              UIClient.Factory.create(uiConfig).openConversationActivity(activity)
              sendEvent(
                "onChatOpened",
                mapOf("conversationId" to conversationId.toString())
              )
              promise.resolve(true)
            }
          } else {
            promise.reject(
              "ERR_REMOTE_CONFIG",
              "Falha ao recuperar config remota",
              null
            )
          }
        }
      }
    }
  }

  private fun getOrCreateConversationId(): UUID {
    loadConversationId()?.let { return it }
    val newId = UUID.randomUUID()
    return newId
  }

  private fun saveConversationId() {
    currentConversationId?.let {
      prefs().edit().putString(conversationIdKey, it.toString()).apply()
    }
  }

  private fun loadConversationId(): UUID? {
    val value = prefs().getString(conversationIdKey, null) ?: return null
    return runCatching { UUID.fromString(value) }.getOrNull()
  }

  private fun deleteConversationId() {
    prefs().edit().remove(conversationIdKey).apply()
    currentConversationId = null
  }

  private fun whenChatReady(promise: Promise?, callback: (client: CoreClient, config: CoreConfiguration) -> Unit) {
    val config = coreConfiguration
    if (config == null) {
      promise?.reject("ERR_NOT_CONFIGURED", "Módulo não configurado. Chame configure() primeiro.", null)
      return
    }

    val context = appContext.reactContext
    if (context == null) {
      promise?.reject("ERR_NO_CONTEXT", "Contexto Android não encontrado", null)
      return
    }

    saveConversationId()

    val client = coreClient ?: CoreClient.Factory.create(context, config).also {
      coreClient = it
      it.registerHiddenPreChatValuesProvider { fields ->
        fields.forEach { f -> hiddenFieldsMap[f.name]?.let { v -> f.userInput = v } }
        fields
      }
      it.start(moduleScope)
    }

    callback(client, config)
  }

  private suspend fun handleCoreEvent(event: CoreEvent) {
    if (event is CoreEvent.ConversationEvent.Entry) {
      val payload = event.conversationEntry.payload
      when (payload) {
        is EntryPayload.CloseConversationPayload -> finalizeSession("session_ended")
        is EntryPayload.SessionStatusChangedPayload -> {
          val status = payload.sessionStatus.toString()
          if (status.contains("Closed", true) || status.contains("Ended", true)) {
            finalizeSession("session_terminated")
          }
        }
        is EntryPayload.QueuePositionPayload -> {
          sendEvent("onQueueUpdate", mapOf("position" to payload.position))
        }
        is EntryPayload.TypingStartedIndicatorPayload -> {
          sendEvent("onTyping", mapOf("isTyping" to true))
        }
        is EntryPayload.TypingStoppedIndicatorPayload -> {
          sendEvent("onTyping", mapOf("isTyping" to false))
        }
        is EntryPayload.MessagePayload -> {
          Log.d(TAG, "Nova mensagem recebida no log: ${payload.abstractMessage}")
        }
        else -> Unit
      }
    }
  }

  private fun finalizeSession(reason: String) {
    deleteConversationId()
    moduleScope.launch(Dispatchers.Main) {
      sendEvent("onChatClosed", mapOf("reason" to reason))
      messagingActivityRef.getAndSet(null)?.finish()
      internalCloseSync()
    }
  }

  private suspend fun internalCloseSync() {
    withContext(Dispatchers.IO) {
      coreClient?.stop()
      coreClient = null
      appContext.reactContext?.let { CoreClient.clearStorage(it) }
    }
  }

  private fun setupLifecycleTracker() {
    val app = appContext.currentActivity?.application ?: return
    activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
      override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        if (activity.localClassName.contains("MessagingInappActivity", ignoreCase = true)) {
          messagingActivityRef.set(activity)
        }
      }
      override fun onActivityDestroyed(activity: Activity) {
        if (activity.localClassName.contains("MessagingInappActivity", ignoreCase = true)) {
          messagingActivityRef.compareAndSet(activity, null)
          sendEvent("onChatClosed", mapOf("reason" to "user_manually_closed"))
          moduleScope.launch { internalCloseSync() }
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
    val app = appContext.currentActivity?.application
    activityLifecycleCallbacks?.let { app?.unregisterActivityLifecycleCallbacks(it) }
    moduleScope.cancel()
  }
}
