package no.nav.su.se.bakover.tilbakekreving.domain

import no.nav.su.se.bakover.common.Beløp
import no.nav.su.se.bakover.common.MånedBeløp
import no.nav.su.se.bakover.common.Månedsbeløp
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.tilMåned
import java.math.BigDecimal

data class Kravgrunnlag(
    val saksnummer: Saksnummer,
    val kravgrunnlagId: String,

    /** Dette er Oppdrag sin ID som er vedlagt i kravgrunnlaget, den er transient i vårt system */
    val vedtakId: String,

    /** Denne er generert av Oppdrag og er vedlagt i kravgrunnlaget, den er transient i vårt system*/
    val kontrollfelt: String,
    val status: KravgrunnlagStatus,

    /**
     * Saksbehandleren/Attestanten knyttet til vedtaket/utbetalinga.
     * Utbetalinga vår kaller dette behandler, så vi gjenbruker det her.
     * Oppdrag har ikke skillet mellom saksbehandler/attestant (men bruker ofte ordet saksbehandler).
     */
    val behandler: NavIdentBruker,
    val utbetalingId: UUID30,
    val grunnlagsperioder: List<Grunnlagsperiode>,
) {

    fun hentBeløpSkalTilbakekreves(): Månedsbeløp {
        return Månedsbeløp(
            grunnlagsperioder
                .map { it.hentBeløpSkalTilbakekreves() }
                .filter { it.sum() > 0 },
        )
    }

    data class Grunnlagsperiode(
        val periode: Periode,
        val beløpSkattMnd: BigDecimal,
        val grunnlagsbeløp: List<Grunnlagsbeløp>,
    ) {

        fun hentBeløpSkalTilbakekreves(): MånedBeløp {
            return MånedBeløp(periode.tilMåned(), Beløp(grunnlagsbeløp.sumOf { it.beløpSkalTilbakekreves.intValueExact() }))
        }

        data class Grunnlagsbeløp(
            val kode: KlasseKode,
            val type: KlasseType,
            val beløpTidligereUtbetaling: BigDecimal,
            val beløpNyUtbetaling: BigDecimal,
            val beløpSkalTilbakekreves: BigDecimal,
            val beløpSkalIkkeTilbakekreves: BigDecimal,
            val skatteProsent: BigDecimal,
        )
    }

    enum class KravgrunnlagStatus {
        ANNU,
        ANOM,
        AVSL,
        BEHA,
        ENDR,
        FEIL,
        MANU,
        NY,
        SPER,
    }
}