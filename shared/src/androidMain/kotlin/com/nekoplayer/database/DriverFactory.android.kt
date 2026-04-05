package com.nekoplayer.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Android 平台数据库驱动实现
 */
actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = NekoDatabase.Schema,
            context = context,
            name = "nekoplayer.db",
            callback = object : AndroidSqliteDriver.Callback(NekoDatabase.Schema) {
                override fun onOpen(db: android.database.sqlite.SQLiteDatabase) {
                    super.onOpen(db)
                    // 启用外键约束，确保删除歌单时关联歌曲被级联删除
                    db.setForeignKeyConstraintsEnabled(true)
                }
            }
        )
    }
}
