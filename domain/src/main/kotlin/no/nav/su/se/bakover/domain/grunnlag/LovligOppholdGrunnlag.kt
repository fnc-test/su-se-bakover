package no.nav.su.se.bakover.domain.grunnlag

import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import java.util.UUID

data class LovligOppholdGrunnlag(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val periode: Periode,
) : Grunnlag, KanPlasseresPåTidslinje<LovligOppholdGrunnlag> {

    fun oppdaterPeriode(periode: Periode): LovligOppholdGrunnlag {
        return tryCreate(
            id = id,
            opprettet = opprettet,
            periode = periode,
        )
    }

    override fun copy(args: CopyArgs.Tidslinje): LovligOppholdGrunnlag = when (args) {
        CopyArgs.Tidslinje.Full -> {
            copy(id = UUID.randomUUID())
        }
        is CopyArgs.Tidslinje.NyPeriode -> {
            copy(id = UUID.randomUUID(), periode = args.periode)
        }
    }

    override fun erLik(other: Grunnlag): Boolean {
        return other is LovligOppholdGrunnlag
    }

    companion object {
        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            periode: Periode,
        ): LovligOppholdGrunnlag = LovligOppholdGrunnlag(
            id = id,
            opprettet = opprettet,
            periode = periode,
        )
    }
}
