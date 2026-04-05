package com.nekoplayer.database

import app.cash.sqldelight.db.SqlDriver

/**
 * 数据库驱动工厂 - 跨平台 expect/actual
 */
expect class DriverFactory {
    fun createDriver(): SqlDriver
}
