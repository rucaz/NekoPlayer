package com.nekoplayer.utils

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * iOS 平台实现
 */
actual fun currentTimeMillis(): Long {
    return (NSDate.date().timeIntervalSince1970 * 1000).toLong()
}
