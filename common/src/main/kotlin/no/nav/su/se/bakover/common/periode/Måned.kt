package no.nav.su.se.bakover.common.periode

import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class Måned private constructor(
    // Vi ønsker ikke ha denne i json enda, men holder oss til Periode sin fraOgMed og tilOgMed
    private val årOgMåned: YearMonth,
) : Periode(årOgMåned) {
    operator fun rangeTo(that: Måned): Periode {
        if (this == that) return this
        return create(this.fraOgMed, that.tilOgMed).also {
            require(this.før(that))
        }
    }

    /**
     * Returns a range from this value up to but excluding the specified to value.
     * If the to value is less than or equal to this value, then the returned range is empty.
     */
    fun until(endExclusive: Måned): List<Måned> {
        return (0 until this.årOgMåned.until(endExclusive.årOgMåned, ChronoUnit.MONTHS)).map {
            this.plusMonths(it)
        }
    }

    fun plusMonths(monthsToAdd: Long): Måned {
        return fra(årOgMåned.plusMonths(monthsToAdd))
    }

    companion object {
        private val factory = CacheingFactory()
        fun now(clock: Clock): Måned {
            return factory.fra(YearMonth.now(clock))
        }

        fun fra(yearMonth: YearMonth): Måned {
            return factory.fra(yearMonth)
        }

        fun fra(fraOgMed: LocalDate, tilOgMed: LocalDate): Måned {
            return factory.fra(fraOgMed, tilOgMed)
        }

        private class CacheingFactory(
            private val cached: MutableMap<YearMonth, Måned> = mutableMapOf(),
        ) {
            fun fra(yearMonth: YearMonth): Måned {
                return cached.getOrPut(yearMonth) { Måned(yearMonth) }
            }

            fun fra(fraOgMed: LocalDate, tilOgMed: LocalDate): Måned {
                require(fraOgMed.year == tilOgMed.year) {
                    "fraOgMed og tilOgMed må være innenfor samme år"
                }
                require(fraOgMed.month == tilOgMed.month) {
                    "fraOgMed og tilOgMed må være innenfor samme måned"
                }
                validateOrThrow(fraOgMed, tilOgMed)
                return fra(YearMonth.of(fraOgMed.year, fraOgMed.month))
            }
        }
    }

    override fun equals(other: Any?) = super.equals(other)
    override fun hashCode() = super.hashCode()
}

/**
 * @throws IllegalArgumentException dersom denne perioden er lengre enn 1 måned.
 */
fun Periode.tilMåned(): Måned {
    require(this.getAntallMåneder() == 1)
    return Måned.fra(YearMonth.of(this.fraOgMed.year, this.fraOgMed.month))
}

/**
 * Mappet med måneder trenger ikke være sortert eller sammenhengende og kan ha duplikater.
 * @throws NoSuchElementException dersom mappet er tomt.
 */
fun <T> Map<Måned, T>.periode() = this.keys.toList().minAndMaxOf()
