package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUføre
import økonomi.domain.simulering.Simulering
import java.time.Clock
import java.util.UUID

data class OpprettetRegulering(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val sakId: UUID,
    override val saksnummer: Saksnummer,
    override val fnr: Fnr,
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering,
    override val periode: Periode = grunnlagsdataOgVilkårsvurderinger.periode()!!,
    override val beregning: Beregning?,
    override val simulering: Simulering?,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val reguleringstype: Reguleringstype,
    override val sakstype: Sakstype,
) : Regulering {
    override fun erÅpen() = true

    /**
     * Skal ikke sende brev ved regulering.
     */
    override fun skalSendeVedtaksbrev(): Boolean {
        return false
    }

    override val erFerdigstilt = false

    init {
        if (reguleringstype == Reguleringstype.AUTOMATISK) {
            require(vilkårsvurderinger.vurdering is Vilkårsvurderingsresultat.Innvilget)
        }
        require(grunnlagsdataOgVilkårsvurderinger.erVurdert())
        require(periode == grunnlagsdataOgVilkårsvurderinger.periode())
        beregning?.let { require(periode == beregning.periode) }
    }

    fun leggTilFradrag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): OpprettetRegulering =
        this.copy(
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                grunnlagsdata = Grunnlagsdata.tryCreate(
                    bosituasjon = grunnlagsdata.bosituasjonSomFullstendig(),
                    fradragsgrunnlag = fradragsgrunnlag,
                ).getOrElse { throw IllegalStateException("Kunne ikke legge til fradrag ved regulering: $it") },
                vilkårsvurderinger = vilkårsvurderinger,
            ),
        )

    fun leggTilUføre(uføregrunnlag: List<Grunnlag.Uføregrunnlag>, clock: Clock): OpprettetRegulering {
        sikkerLogg.debug(
            "Skal legge til {} for regulering {}. Vilkår & grunnlag som er på behandling NÅ: {}, {}",
            uføregrunnlag,
            this.id,
            grunnlagsdata,
            vilkårsvurderinger,
        )

        return this.copy(
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(
                    UføreVilkår.Vurdert.tryCreate(
                        uføregrunnlag.map {
                            VurderingsperiodeUføre.tryCreate(
                                opprettet = Tidspunkt.now(clock),
                                vurdering = Vurdering.Innvilget,
                                grunnlag = it,
                                vurderingsperiode = it.periode,
                            ).getOrElse { throw RuntimeException("$it") }
                        }.toNonEmptyList(),
                    ).getOrElse { throw RuntimeException("$it") },
                ),
            ),
        )
    }

    fun leggTilSaksbehandler(saksbehandler: NavIdentBruker.Saksbehandler): OpprettetRegulering =
        this.copy(
            saksbehandler = saksbehandler,
        )

    fun beregn(
        satsFactory: SatsFactory,
        begrunnelse: String? = null,
        clock: Clock,
    ): Either<KunneIkkeBeregneRegulering, OpprettetRegulering> {
        return this.utførBeregning(
            satsFactory = satsFactory,
            begrunnelse = begrunnelse,
            clock = clock,
        ).map { this.copy(beregning = it) }
    }

    fun simuler(
        simuler: (beregning: Beregning, uføregrunnlag: NonEmptyList<Grunnlag.Uføregrunnlag>?) -> Either<SimulerUtbetalingFeilet, Simulering>,
    ): Either<KunneIkkeSimulereRegulering, OpprettetRegulering> {
        return simuler(
            beregning ?: return KunneIkkeSimulereRegulering.FantIngenBeregning.left(),
            when (sakstype) {
                Sakstype.ALDER -> {
                    null
                }

                Sakstype.UFØRE -> {
                    vilkårsvurderinger.uføreVilkår()
                        .getOrElse { throw IllegalStateException("Regulering uføre: $id mangler uføregrunnlag") }
                        .grunnlag
                        .toNonEmptyList()
                }
            },
        ).mapLeft { KunneIkkeSimulereRegulering.SimuleringFeilet }
            .map { copy(simulering = it) }
    }

    fun avslutt(avsluttetAv: NavIdentBruker, clock: Clock): AvsluttetRegulering {
        return AvsluttetRegulering(
            opprettetRegulering = this,
            avsluttetTidspunkt = Tidspunkt.now(clock),
            avsluttetAv = avsluttetAv,
        )
    }

    fun tilIverksatt(): IverksattRegulering =
        IverksattRegulering(opprettetRegulering = this, beregning!!, simulering!!)

    /**
     * @return samler RuntimeException i en left.
     */
    private fun utførBeregning(
        satsFactory: SatsFactory,
        begrunnelse: String?,
        clock: Clock,
    ): Either<KunneIkkeBeregneRegulering.BeregningFeilet, Beregning> {
        return Either.catch {
            BeregningStrategyFactory(
                clock = clock,
                satsFactory = satsFactory,
            ).beregn(this, begrunnelse)
        }.mapLeft {
            KunneIkkeBeregneRegulering.BeregningFeilet(feil = it)
        }
    }
}
