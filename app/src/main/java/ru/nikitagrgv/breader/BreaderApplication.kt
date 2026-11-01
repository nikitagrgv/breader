package ru.nikitagrgv.breader

import android.app.Application
import ru.nikitagrgv.breader.data.BookFiles
import ru.nikitagrgv.breader.data.LibraryRepository
import ru.nikitagrgv.breader.data.SettingsRepository

class BreaderApplication : Application() {
    val libraryRepository by lazy { LibraryRepository(this) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val bookFiles by lazy { BookFiles(this) }
}
