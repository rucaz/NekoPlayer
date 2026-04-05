package com.nekoplayer.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

/**
 * iOS 平台数据库驱动实现
 */
actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(NekoDatabase.Schema, "nekoplayer.db")
    }
}
