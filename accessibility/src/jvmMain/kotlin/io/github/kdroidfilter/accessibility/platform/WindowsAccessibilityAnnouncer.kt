package io.github.kdroidfilter.accessibility.platform

import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.Dispatch
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.OaIdl
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.OleAuto
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.Variant
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.platform.win32.WinDef.LCID
import com.sun.jna.platform.win32.WinDef.WORD
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import io.github.kdroidfilter.accessibility.AnnouncementPriority

internal class WindowsAccessibilityAnnouncer : PlatformAnnouncer {
    private val uiaLibrary = NativeLibrary.getInstance("UIAutomationCore")
    private val hostProviderFromHwnd: Function = uiaLibrary.getFunction("UiaHostProviderFromHwnd")
    private val raiseNotificationEvent: Function? = runCatching {
        uiaLibrary.getFunction("UiaRaiseNotificationEvent")
    }.getOrNull()
    private val nvdaClient: NvdaControllerClient? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        loadNvdaClient()
    }
    private val jawsClsid = Guid.CLSID("{CCE5B1E5-B2ED-45D5-B09F-8EC54B75ABF4}")
    private val jawsIid = Guid.IID("{123DEDB4-2CF6-429C-A2AB-CC809E5516CE}")
    private val iidNullRef = Guid.REFIID(Guid.IID_NULL)

    override val isSupported: Boolean = raiseNotificationEvent != null || nvdaClient != null

    override fun announce(message: String, priority: AnnouncementPriority): Boolean {
        if (tryNvdaAnnounce(message, priority)) {
            return true
        }
        val hrInit = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED)
        val hrInitValue = hrInit.toInt()
        val comInitialized = hrInitValue == COMUtils.S_OK || hrInitValue == COMUtils.S_FALSE
        val comAlreadyInitialized = hrInitValue == WinError.RPC_E_CHANGED_MODE
        if (!comInitialized && !comAlreadyInitialized) {
            return false
        }
        try {
            if (tryJawsAnnounce(message, priority)) {
                return true
            }
            val raiseEvent = raiseNotificationEvent ?: return false
            return tryUiaAnnounce(raiseEvent, message, priority)
        } finally {
            if (comInitialized) {
                Ole32.INSTANCE.CoUninitialize()
            }
        }
    }

    private fun tryNvdaAnnounce(message: String, priority: AnnouncementPriority): Boolean {
        val client = nvdaClient ?: return false
        return try {
            if (client.nvdaController_testIfRunning() != 0) {
                return false
            }
            if (priority == AnnouncementPriority.ASSERTIVE) {
                client.nvdaController_cancelSpeech()
            }
            client.nvdaController_speakText(WString(message)) == 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun tryJawsAnnounce(message: String, priority: AnnouncementPriority): Boolean {
        val dispatchRef = PointerByReference()
        val hrCreate = Ole32.INSTANCE.CoCreateInstance(
            jawsClsid,
            Pointer.NULL,
            WTypes.CLSCTX_INPROC_SERVER,
            jawsIid,
            dispatchRef,
        )
        if (COMUtils.FAILED(hrCreate)) {
            return false
        }
        val dispatchPtr = dispatchRef.value
        if (dispatchPtr == null || dispatchPtr == Pointer.NULL) {
            return false
        }
        val dispatch = Dispatch(dispatchPtr)
        val excepInfo = OaIdl.EXCEPINFO.ByReference()
        var messageVariant: Variant.VARIANT? = null
        try {
            val dispIdRef = OaIdl.DISPIDByReference()
            val hrName = dispatch.GetIDsOfNames(
                iidNullRef,
                arrayOf(WString("SayString")),
                1,
                LCID(0),
                dispIdRef,
            )
            if (COMUtils.FAILED(hrName)) {
                return false
            }
            val flushVariant = Variant.VARIANT(priority == AnnouncementPriority.ASSERTIVE)
            val localMessageVariant = Variant.VARIANT(message)
            messageVariant = localMessageVariant
            val dispParams = OleAuto.DISPPARAMS.ByReference().apply {
                setArgs(arrayOf(flushVariant, localMessageVariant))
            }
            val result = Variant.VARIANT.ByReference()
            val argErr = IntByReference()
            val hrInvoke = dispatch.Invoke(
                dispIdRef.value,
                iidNullRef,
                LCID(0),
                WORD(OleAuto.DISPATCH_METHOD.toLong()),
                dispParams,
                result,
                excepInfo,
                argErr,
            )
            if (COMUtils.FAILED(hrInvoke)) {
                return false
            }
            return result.booleanValue()
        } finally {
            messageVariant?.let { OleAuto.INSTANCE.VariantClear(it) }
            freeExcepInfo(excepInfo)
            dispatch.Release()
        }
    }

    private fun tryUiaAnnounce(
        raiseEvent: Function,
        message: String,
        priority: AnnouncementPriority,
    ): Boolean {
        val hwnd = User32.INSTANCE.GetForegroundWindow() ?: return false
        val providerRef = PointerByReference()
        val hrProvider = hostProviderFromHwnd.invoke(
            WinNT.HRESULT::class.java,
            arrayOf(hwnd, providerRef),
        ) as WinNT.HRESULT
        if (COMUtils.FAILED(hrProvider)) {
            return false
        }
        val provider = providerRef.value
        if (provider == null || provider == Pointer.NULL) {
            return false
        }
        return try {
            val processing = when (priority) {
                AnnouncementPriority.ASSERTIVE -> NotificationProcessing.IMPORTANT_MOST_RECENT
                AnnouncementPriority.POLITE -> NotificationProcessing.MOST_RECENT
            }
            val hrAnnounce = raiseEvent.invoke(
                WinNT.HRESULT::class.java,
                arrayOf(
                    provider,
                    NotificationKind.OTHER,
                    processing,
                    WString(message),
                    WString("desktop.accessibility.announce"),
                ),
            ) as WinNT.HRESULT
            COMUtils.SUCCEEDED(hrAnnounce)
        } finally {
            Unknown(provider).Release()
        }
    }

    private fun freeExcepInfo(excepInfo: OaIdl.EXCEPINFO.ByReference) {
        excepInfo.bstrSource?.let { OleAuto.INSTANCE.SysFreeString(it) }
        excepInfo.bstrDescription?.let { OleAuto.INSTANCE.SysFreeString(it) }
        excepInfo.bstrHelpFile?.let { OleAuto.INSTANCE.SysFreeString(it) }
    }

    private fun loadNvdaClient(): NvdaControllerClient? {
        val candidates = listOf(
            "nvdaControllerClient",
            "nvdaControllerClient64",
            "nvdaControllerClient32",
        )
        for (name in candidates) {
            val client = runCatching { Native.load(name, NvdaControllerClient::class.java) }.getOrNull()
            if (client != null) {
                return client
            }
        }
        return null
    }

    private interface NvdaControllerClient : StdCallLibrary {
        fun nvdaController_testIfRunning(): Int
        fun nvdaController_cancelSpeech(): Int
        fun nvdaController_speakText(text: WString): Int
    }

    private object NotificationKind {
        const val OTHER = 4
    }

    private object NotificationProcessing {
        const val IMPORTANT_MOST_RECENT = 1
        const val MOST_RECENT = 3
    }
}
