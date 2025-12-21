package io.github.kdroidfilter.accessibility.platform

import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.PointerByReference
import io.github.kdroidfilter.accessibility.AnnouncementPriority

internal class WindowsAccessibilityAnnouncer : PlatformAnnouncer {
    private val uiaLibrary = NativeLibrary.getInstance("UIAutomationCore")
    private val hostProviderFromHwnd: Function = uiaLibrary.getFunction("UiaHostProviderFromHwnd")
    private val raiseNotificationEvent: Function? = runCatching {
        uiaLibrary.getFunction("UiaRaiseNotificationEvent")
    }.getOrNull()

    override val isSupported: Boolean = raiseNotificationEvent != null

    override fun announce(message: String, priority: AnnouncementPriority): Boolean {
        val raiseEvent = raiseNotificationEvent ?: return false
        val hrInit = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED)
        val hrInitValue = hrInit.toInt()
        val comInitialized = hrInitValue == COMUtils.S_OK || hrInitValue == COMUtils.S_FALSE
        val comAlreadyInitialized = hrInitValue == WinError.RPC_E_CHANGED_MODE
        if (!comInitialized && !comAlreadyInitialized) {
            return false
        }
        try {
            val hwnd = User32.INSTANCE.GetForegroundWindow() ?: return false
            val providerRef = PointerByReference()
            val hrProvider = hostProviderFromHwnd.invoke(
                WinNT.HRESULT::class.java,
                arrayOf(hwnd, providerRef),
            ) as WinNT.HRESULT
            if (COMUtils.FAILED(hrProvider)) {
                return false
            }
            val provider = providerRef.value ?: return false
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
        } finally {
            if (comInitialized) {
                Ole32.INSTANCE.CoUninitialize()
            }
        }
    }

    private object NotificationKind {
        const val OTHER = 4
    }

    private object NotificationProcessing {
        const val IMPORTANT_MOST_RECENT = 1
        const val MOST_RECENT = 3
    }
}
