package com.cola.record

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cola.record.data.AppDatabase
import com.cola.record.data.sync.SyncManager
import com.cola.record.data.sync.SyncSettings

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `outing` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`place` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, " +
                "`rating` INTEGER NOT NULL, " +
                "`minutes` INTEGER NOT NULL, " +
                "`time` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`photos` TEXT NOT NULL)"
        )
    }
}

class ColaApp : Application() {
    val db: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "cola_record.db")
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }

    val syncSettings: SyncSettings by lazy { SyncSettings(this) }

    val syncManager: SyncManager by lazy {
        SyncManager(this, db, syncSettings)
    }

    override fun onCreate() {
        super.onCreate()
        // 上次退出前还有未上传的改动时，启动后补传
        syncManager.ensureScheduled(immediate = true)
    }
}
