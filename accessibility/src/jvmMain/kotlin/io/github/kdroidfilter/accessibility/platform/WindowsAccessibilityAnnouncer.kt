package io.github.kdroidfilter.accessibility.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.Dispatch
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.OaIdl
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.OleAuto
import com.sun.jna.platform.win32.Variant
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.platform.win32.WinDef.LCID
import com.sun.jna.platform.win32.WinDef.WORD
import com.sun.jna.platform.win32.WinError
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import io.github.kdroidfilter.accessibility.AnnouncementPriority
import io.github.kdroidfilter.accessibility.tools.debugln
import io.github.kdroidfilter.accessibility.tools.infoln
import io.github.kdroidfilter.accessibility.tools.warnln
import io.github.kdroidfilter.accessibility.tools.verboseln

internal class WindowsAccessibilityAnnouncer : PlatformAnnouncer {
    private val nvdaClient: NvdaControllerClient? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        loadNvdaClient()
    }
    private val jawsClsid = Guid.CLSID("{CCE5B1E5-B2ED-45D5-B09F-8EC54B75ABF4}")
    private val jawsIid = Guid.IID("{123DEDB4-2CF6-429C-A2AB-CC809E5516CE}")
    private val iidNullRef = Guid.REFIID(Guid.IID_NULL)
    private val jawsAvailable: Boolean by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        checkJawsAvailable()
    }

    override val isSupported: Boolean
        get() = nvdaClient != null || jawsAvailable

    override fun announce(message: String, priority: AnnouncementPriority): Boolean {
        verboseln { "Windows announcer: announce requested (priority=$priority, length=${message.length})." }
        if (tryNvdaAnnounce(message, priority)) {
            infoln { "Windows announcer: announced via NVDA." }
            return true
        }
        val hrInit = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED)
        val hrInitValue = hrInit.toInt()
        val comInitialized = hrInitValue == COMUtils.S_OK || hrInitValue == COMUtils.S_FALSE
        val comAlreadyInitialized = hrInitValue == WinError.RPC_E_CHANGED_MODE
        if (!comInitialized && !comAlreadyInitialized) {
            warnln { "Windows announcer: COM init failed (hr=${formatHresult(hrInitValue)})." }
            return false
        }
        try {
            if (tryJawsAnnounce(message, priority)) {
                infoln { "Windows announcer: announced via JAWS." }
                return true
            }
            warnln { "Windows announcer: no announcer available for request." }
            return false
        } finally {
            if (comInitialized) {
                Ole32.INSTANCE.CoUninitialize()
            }
        }
    }

    private fun tryNvdaAnnounce(message: String, priority: AnnouncementPriority): Boolean {
        val client = nvdaClient ?: run {
            debugln { "Windows announcer: NVDA controller not available." }
            return false
        }
        return try {
            if (client.nvdaController_testIfRunning() != 0) {
                debugln { "Windows announcer: NVDA not running." }
                return false
            }
            if (priority == AnnouncementPriority.ASSERTIVE) {
                client.nvdaController_cancelSpeech()
            }
            val result = client.nvdaController_speakText(WString(message)) == 0
            if (!result) {
                warnln { "Windows announcer: NVDA speakText failed." }
            }
            result
        } catch (_: Throwable) {
            warnln { "Windows announcer: NVDA announce threw an exception." }
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
            warnln { "Windows announcer: JAWS COM instance unavailable (hr=${formatHresult(hrCreate.toInt())})." }
            return false
        }
        val dispatchPtr = dispatchRef.value
        if (dispatchPtr == null || dispatchPtr == Pointer.NULL) {
            warnln { "Windows announcer: JAWS COM pointer missing." }
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
                warnln { "Windows announcer: JAWS SayString not available (hr=${formatHresult(hrName.toInt())})." }
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
                warnln { "Windows announcer: JAWS SayString invoke failed (hr=${formatHresult(hrInvoke.toInt())})." }
                return false
            }
            return result.booleanValue()
        } finally {
            messageVariant?.let { OleAuto.INSTANCE.VariantClear(it) }
            freeExcepInfo(excepInfo)
            dispatch.Release()
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
            debugln { "Windows announcer: trying NVDA client '$name'." }
            val client = runCatching { Native.load(name, NvdaControllerClient::class.java) }.getOrNull()
            if (client != null) {
                infoln { "Windows announcer: NVDA client '$name' loaded." }
                return client
            }
        }
        debugln { "Windows announcer: no NVDA client library found." }
        return null
    }

    private fun checkJawsAvailable(): Boolean {
        val hrInit = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED)
        val hrInitValue = hrInit.toInt()
        val comInitialized = hrInitValue == COMUtils.S_OK || hrInitValue == COMUtils.S_FALSE
        val comAlreadyInitialized = hrInitValue == WinError.RPC_E_CHANGED_MODE
        if (!comInitialized && !comAlreadyInitialized) {
            debugln { "Windows announcer: COM init failed while checking JAWS (hr=${formatHresult(hrInitValue)})." }
            return false
        }
        return try {
            val dispatchRef = PointerByReference()
            val hrCreate = Ole32.INSTANCE.CoCreateInstance(
                jawsClsid,
                Pointer.NULL,
                WTypes.CLSCTX_INPROC_SERVER,
                jawsIid,
                dispatchRef,
            )
            if (COMUtils.FAILED(hrCreate)) {
                debugln { "Windows announcer: JAWS COM class missing (hr=${formatHresult(hrCreate.toInt())})." }
                return false
            }
            val dispatchPtr = dispatchRef.value
            if (dispatchPtr == null || dispatchPtr == Pointer.NULL) {
                debugln { "Windows announcer: JAWS COM pointer missing during probe." }
                return false
            }
            val dispatch = Dispatch(dispatchPtr)
            dispatch.Release()
            true
        } finally {
            if (comInitialized) {
                Ole32.INSTANCE.CoUninitialize()
            }
        }
    }

    private fun formatHresult(value: Int): String {
        return String.format("0x%08X", value)
    }

    private interface NvdaControllerClient : StdCallLibrary {
        fun nvdaController_testIfRunning(): Int
        fun nvdaController_cancelSpeech(): Int
        fun nvdaController_speakText(text: WString): Int
    }

}
