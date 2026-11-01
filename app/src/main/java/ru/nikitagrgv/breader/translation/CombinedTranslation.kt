package ru.nikitagrgv.breader.translation

/** A translation and the models that gave it. */
data class Variant(val text: String, val models: List<String>)

/** Translations from several models, merged. */
data class CombinedTranslation(
    /** The ones given by more models come first. */
    val variants: List<Variant>,
    /** Translations that the models offered as alternatives only, the most offered first. */
    val alternatives: List<String>,
    /** Pairs of a model and its note. */
    val notes: List<Pair<String, String>>,
)

/** @param translations pairs of a model and its translation. */
fun combine(translations: List<Pair<String, Translation>>): CombinedTranslation {
    class Group(val text: String) {
        val models = ArrayList<String>()

        /** How many models offered it as an alternative: the second opinion, used to rank equal groups. */
        var mentions = 0
    }

    val groups = LinkedHashMap<String, Group>()
    for ((model, translation) in translations) {
        groups.getOrPut(translation.text.translationKey()) { Group(translation.text) }.models += model
    }
    val others = LinkedHashMap<String, Group>()
    for ((_, translation) in translations) {
        for (alternative in translation.alternatives) {
            val key = alternative.translationKey()
            val group = groups[key] ?: others.getOrPut(key) { Group(alternative) }
            group.mentions++
        }
    }
    // Stable sorts: equal ones stay in the order of the models.
    return CombinedTranslation(
        variants = groups.values
            .sortedWith(compareByDescending<Group> { it.models.size }.thenByDescending { it.mentions })
            .map { Variant(it.text, it.models) },
        alternatives = others.values.sortedByDescending { it.mentions }.map { it.text },
        notes = translations.filter { it.second.note.isNotBlank() }.map { it.first to it.second.note },
    )
}
