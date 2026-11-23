package ru.nikitagrgv.breader

import android.app.Application
import kotlinx.coroutines.flow.map
import ru.nikitagrgv.breader.data.BookFiles
import ru.nikitagrgv.breader.data.FavoritesRepository
import ru.nikitagrgv.breader.data.LibraryRepository
import ru.nikitagrgv.breader.data.SettingsRepository
import ru.nikitagrgv.breader.dictionary.Dictionaries
import ru.nikitagrgv.breader.dictionary.DictionaryRepository
import ru.nikitagrgv.breader.translation.OpenRouter
import ru.nikitagrgv.breader.translation.TranslationCache
import ru.nikitagrgv.breader.translation.UsageLog

class BreaderApplication : Application() {
    val libraryRepository by lazy { LibraryRepository(this) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val bookFiles by lazy { BookFiles(this) }
    val usageLog by lazy { UsageLog(this) }
    val openRouter by lazy { OpenRouter(onUsage = usageLog::add) }
    val translationCache by lazy { TranslationCache(this, settingsRepository.settings.map { it.translation.cacheSize }) }
    val dictionaryRepository by lazy { DictionaryRepository(this) }
    val dictionaries by lazy { Dictionaries(dictionaryRepository, settingsRepository.settings) }
    val favoritesRepository by lazy { FavoritesRepository(this) }
}
