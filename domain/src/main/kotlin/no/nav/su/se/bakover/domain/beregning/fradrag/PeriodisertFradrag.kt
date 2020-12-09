package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.Periode

internal data class PeriodisertFradrag(
    private val type: Fradragstype,
    private val månedsbeløp: Double,
    private val periode: Periode,
    private val utenlandskInntekt: UtenlandskInntekt? = null,
    private val tilhører: FradragTilhører
) : Fradrag {
    init {
        require(månedsbeløp >= 0) { "Fradrag kan ikke være negative" }
        require(periode.getAntallMåneder() == 1) { "Periodiserte fradrag kan bare gjelde for en enkelt måned" }
    }

    override fun getTilhører(): FradragTilhører = tilhører
    override fun getFradragstype(): Fradragstype = type
    override fun getMånedsbeløp(): Double = månedsbeløp
    override fun getUtenlandskInntekt(): UtenlandskInntekt? = utenlandskInntekt
    override fun getPeriode(): Periode = periode
}
