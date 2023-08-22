package no.nav.su.se.bakover.hendelse.domain

@JvmInline
value class Hendelsesversjon(val value: Long) : Comparable<Hendelsesversjon> {

    init {
        require(value > 0L) { "Versjonen må være større enn 0L" }
    }

    override fun compareTo(other: Hendelsesversjon): Int {
        return this.value.compareTo(other.value)
    }

    operator fun inc() = Hendelsesversjon(this.value + 1)

    override fun toString() = value.toString()

    companion object {
        /**
         * nr 1 er reservert for ny sak (se versjon i SakPostgresRepo)
         */
        fun ny(): Hendelsesversjon = Hendelsesversjon(2)

        fun max(first: Hendelsesversjon, second: Hendelsesversjon): Hendelsesversjon =
            if (first > second) first else second

        fun max(first: Hendelsesversjon?, second: Hendelsesversjon): Hendelsesversjon = if (first == null) second else max(first, second)

        fun max(first: Hendelsesversjon, second: Hendelsesversjon?): Hendelsesversjon = if (second == null) first else max(first, second)
    }
}
