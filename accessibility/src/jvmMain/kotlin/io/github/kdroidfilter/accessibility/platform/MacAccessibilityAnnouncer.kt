package io.github.kdroidfilter.accessibility.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.platform.mac.CoreFoundation
import com.sun.jna.ptr.IntByReference
import io.github.kdroidfilter.accessibility.AnnouncementPriority
import io.github.kdroidfilter.accessibility.tools.debugln
import io.github.kdroidfilter.accessibility.tools.infoln
import io.github.kdroidfilter.accessibility.tools.warnln
import io.github.kdroidfilter.accessibility.tools.verboseln

internal class MacAccessibilityAnnouncer : PlatformAnnouncer {
    private val coreFoundation = CoreFoundation.INSTANCE
    private val appKit = Native.load("AppKit", AppKit::class.java)
    private val objc = Native.load("objc", ObjCRuntime::class.java)
    private val objcMsgSend = NativeLibrary.getInstance("objc").getFunction("objc_msgSend")

    private val nsApplication: Pointer? = runCatching {
        val nsApplicationClass = objc.objc_getClass("NSApplication")
        val sharedSelector = objc.sel_registerName("sharedApplication")
        if (nsApplicationClass == null || sharedSelector == null) {
            null
        } else {
            objcMsgSend.invoke(Pointer::class.java, arrayOf(nsApplicationClass, sharedSelector)) as Pointer
        }
    }.getOrNull()

    private val notificationName = loadStringConstant(
        "NSAccessibilityAnnouncementRequestedNotification",
        "NSAccessibilityAnnouncementRequestedNotification",
    )
    private val announcementKey = loadStringConstant(
        "NSAccessibilityAnnouncementKey",
        "NSAccessibilityAnnouncementKey",
    )
    private val priorityKey = loadStringConstant(
        "NSAccessibilityPriorityKey",
        "NSAccessibilityPriorityKey",
    )

    override val isSupported: Boolean = nsApplication != null

    override fun announce(message: String, priority: AnnouncementPriority): Boolean {
        verboseln { "Mac announcer: announce requested (priority=$priority, length=${message.length})." }
        val nsApp = nsApplication ?: run {
            warnln { "Mac announcer: NSApplication unavailable." }
            return false
        }
        val userInfo = coreFoundation.CFDictionaryCreateMutable(
            null,
            CoreFoundation.CFIndex(0),
            null,
            null,
        ) ?: run {
            warnln { "Mac announcer: failed to create CFDictionary." }
            return false
        }
        val announcement = CoreFoundation.CFStringRef.createCFString(message) ?: run {
            coreFoundation.CFRelease(userInfo)
            warnln { "Mac announcer: failed to create CFString for message." }
            return false
        }
        val priorityNumber = coreFoundation.CFNumberCreate(
            null,
            CoreFoundation.CFNumberType.kCFNumberIntType.typeIndex(),
            IntByReference(macPriority(priority)),
        )
        return try {
            userInfo.setValue(announcementKey, announcement)
            if (priorityNumber != null) {
                userInfo.setValue(priorityKey, priorityNumber)
            } else {
                debugln { "Mac announcer: failed to create CFNumber for priority." }
            }
            appKit.NSAccessibilityPostNotificationWithUserInfo(
                nsApp,
                notificationName.pointer,
                userInfo.pointer,
            )
            infoln { "Mac announcer: announcement posted." }
            true
        } finally {
            coreFoundation.CFRelease(announcement)
            if (priorityNumber != null) {
                coreFoundation.CFRelease(priorityNumber)
            }
            coreFoundation.CFRelease(userInfo)
        }
    }

    private fun macPriority(priority: AnnouncementPriority): Int = when (priority) {
        AnnouncementPriority.ASSERTIVE -> 2
        AnnouncementPriority.POLITE -> 0
    }

    private fun loadStringConstant(symbol: String, fallback: String): CoreFoundation.CFStringRef {
        val appKitLibrary = NativeLibrary.getInstance("AppKit")
        val pointer = runCatching {
            appKitLibrary.getGlobalVariableAddress(symbol).getPointer(0)
        }.getOrNull()
        if (pointer != null) {
            return runCatching { CoreFoundation.CFStringRef(pointer) }.getOrElse { createFallback(fallback) }
        }
        return createFallback(fallback)
    }

    private fun createFallback(value: String): CoreFoundation.CFStringRef {
        return CoreFoundation.CFStringRef.createCFString(value)
            ?: throw IllegalStateException("Failed to create CFString for $value")
    }

    private interface AppKit : Library {
        fun NSAccessibilityPostNotificationWithUserInfo(
            element: Pointer?,
            notification: Pointer?,
            userInfo: Pointer?,
        )
    }

    private interface ObjCRuntime : Library {
        fun objc_getClass(name: String): Pointer?
        fun sel_registerName(name: String): Pointer?
    }
}
