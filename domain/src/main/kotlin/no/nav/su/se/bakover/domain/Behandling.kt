package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningDto
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class Behandling constructor(
    id: UUID = UUID.randomUUID(),
    opprettet: Instant = Instant.now(),
    private val vilkårsvurderinger: MutableList<Vilkårsvurdering> = mutableListOf(),
    private val søknad: Søknad,
    private val beregninger: MutableList<Beregning> = mutableListOf()
) : PersistentDomainObject<BehandlingPersistenceObserver>(id, opprettet), DtoConvertable<BehandlingDto> {

    override fun toDto() = BehandlingDto(
        id = id,
        opprettet = opprettet,
        vilkårsvurderinger = vilkårsvurderinger.map { it.toDto() },
        søknad = søknad.toDto(),
        beregninger = beregninger.map { it.toDto() }
    )

    fun opprettVilkårsvurderinger(): MutableList<Vilkårsvurdering> {
        vilkårsvurderinger.addAll(
            persistenceObserver.opprettVilkårsvurderinger(
                behandlingId = id,
                vilkårsvurderinger = Vilkår.values().map { Vilkårsvurdering(vilkår = it) })
        )
        return vilkårsvurderinger
    }

    fun oppdaterVilkårsvurderinger(oppdatertListe: List<Vilkårsvurdering>): List<Vilkårsvurdering> {
        oppdatertListe.forEach { oppdatert ->
            vilkårsvurderinger
                .single { it == oppdatert }
                .apply { oppdater(oppdatert) }
        }
        return vilkårsvurderinger
    }

    fun opprettBeregning(
        fom: LocalDate = LocalDate.now(),
        tom: LocalDate = fom.plusMonths(12).minusDays(1),
        sats: Sats = Sats.HØY
    ): Beregning {
        val beregning = persistenceObserver.opprettBeregning(
            behandlingId = id,
            beregning = Beregning(
                fom = fom,
                tom = tom,
                sats = sats
            )
        )
        beregninger.add(beregning)
        return beregning
    }

    override fun equals(other: Any?) = other is Behandling && id == other.id
    override fun hashCode() = id.hashCode()
}

interface BehandlingPersistenceObserver : PersistenceObserver {
    fun opprettVilkårsvurderinger(
        behandlingId: UUID,
        vilkårsvurderinger: List<Vilkårsvurdering>
    ): List<Vilkårsvurdering>

    fun opprettBeregning(behandlingId: UUID, beregning: Beregning): Beregning
}

data class BehandlingDto(
    val id: UUID,
    val opprettet: Instant,
    val vilkårsvurderinger: List<VilkårsvurderingDto>,
    val søknad: SøknadDto,
    val beregninger: List<BeregningDto>
)
