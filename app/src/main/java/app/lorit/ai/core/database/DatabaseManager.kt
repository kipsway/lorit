package app.lorit.ai.core.database

import android.content.Context
import androidx.room.Room
import app.lorit.ai.core.security.EncryptedPreferencesVault
import app.lorit.ai.core.security.SecureVault
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * Менеджер баз данных приложения Lorit.
 * 
 * Отвечает за:
 * 1. Инициализацию зашифрованной базы данных Room с помощью SQLCipher (AES-256).
 * 2. Управление сессиями «Инкогнито» через оперативную память (inMemoryDatabaseBuilder).
 */
class DatabaseManager private constructor(
    private val context: Context,
    private val secureVault: SecureVault
) {

    private val encryptedDb: AppDatabase by lazy {
        initEncryptedDatabase()
    }

    @Volatile
    private var inMemoryDb: AppDatabase? = null

    /**
     * Возвращает основную зашифрованную базу данных (SQLCipher AES-256).
     */
    fun getEncryptedDatabase(): AppDatabase = encryptedDb

    /**
     * Создает или возвращает существующую сессию базы данных в оперативной памяти (Режим Инкогнито).
     */
    @Synchronized
    fun getOrCreateInMemoryDatabase(): AppDatabase {
        val existing = inMemoryDb
        if (existing != null && existing.isOpen) {
            return existing
        }
        val newDb = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        )
            .fallbackToDestructiveMigration()
            .build()
        inMemoryDb = newDb
        return newDb
    }

    /**
     * Мгновенно стирает сессию Инкогнито из оперативной памяти при закрытии временного чата.
     */
    @Synchronized
    fun purgeInMemoryDatabase() {
        inMemoryDb?.let { db ->
            if (db.isOpen) {
                db.close()
            }
        }
        inMemoryDb = null
    }

    /**
     * Возвращает целевую БД в зависимости от флага isTemporary.
     */
    fun getDatabaseFor(isTemporary: Boolean): AppDatabase {
        return if (isTemporary) getOrCreateInMemoryDatabase() else getEncryptedDatabase()
    }

    private fun initEncryptedDatabase(): AppDatabase {
        // Инициализируем нативные библиотеки SQLCipher
        SQLiteDatabase.loadLibs(context)

        val passphrase = secureVault.getDatabasePassphrase()
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DATABASE_NAME
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    companion object {
        private const val DATABASE_NAME = "lorit_ai_encrypted.db"

        @Volatile
        private var instance: DatabaseManager? = null

        fun getInstance(context: Context): DatabaseManager {
            return instance ?: synchronized(this) {
                val vault = EncryptedPreferencesVault.getInstance(context)
                instance ?: DatabaseManager(context.applicationContext, vault).also {
                    instance = it
                }
            }
        }
    }
}
