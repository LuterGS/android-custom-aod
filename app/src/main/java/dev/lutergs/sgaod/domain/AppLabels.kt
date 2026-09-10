package dev.lutergs.sgaod.domain

object AppLabels {
    private val controls = Regex("[\\p{Cntrl}\\u202A-\\u202E\\u2066-\\u2069]")

    fun readable(value: CharSequence?, packageName: String): String? = value?.toString()
        ?.replace(controls, " ")?.trim()?.takeIf { it.isNotEmpty() && it != packageName }?.take(80)
}
