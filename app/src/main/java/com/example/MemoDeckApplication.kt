package com.example

import android.app.Application
import com.example.backup.BackupManager
import com.example.database.MemoDeckDatabase
import com.example.ocr.OfflineOcrEngine
import com.example.pdf_import.OfflinePdfProcessor
import com.example.repositories.MemoDeckRepository
import com.example.repositories.SettingsRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class AppContainer(application: Application) {
    val database: MemoDeckDatabase = MemoDeckDatabase.getInstance(application)
    val repository: MemoDeckRepository = MemoDeckRepository(database)
    val settingsRepository: SettingsRepository = SettingsRepository(application)
    val backupManager: BackupManager = BackupManager(repository, settingsRepository)
    val ocrEngine: OfflineOcrEngine = OfflineOcrEngine()
    val pdfProcessor: OfflinePdfProcessor = OfflinePdfProcessor(application, ocrEngine)
}

class MemoDeckApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
    }
}
