package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import økonomi.domain.simulering.Simulering
import java.util.UUID

/**
 * https://jira.adeo.no/browse/BEGREP-304 og https://jira.adeo.no/browse/BEGREP-2321
 */
interface Stønadsbehandling {
    val id: UUID
    val opprettet: Tidspunkt
    val sakId: UUID
    val saksnummer: Saksnummer
    val fnr: Fnr
    val periode: Periode
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger

    val sakstype: Sakstype

    val beregning: Beregning?
    val simulering: Simulering?

    val grunnlagsdata: Grunnlagsdata get() = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata
    val vilkårsvurderinger: Vilkårsvurderinger get() = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger
    val eksterneGrunnlag: EksterneGrunnlag get() = grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag
    fun sakinfo(): SakInfo {
        return SakInfo(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            type = sakstype,
        )
    }
    fun skalSendeVedtaksbrev(): Boolean
}

interface BehandlingMedOppgave : Stønadsbehandling {
    val oppgaveId: OppgaveId
}

interface BehandlingMedAttestering : Stønadsbehandling {
    val attesteringer: Attesteringshistorikk

    fun hentAttestantSomIverksatte(): NavIdentBruker.Attestant? {
        return this.attesteringer.hentSisteIverksatteAttesteringOrNull()?.attestant
    }
    fun prøvHentSisteAttestering(): Attestering? = attesteringer.prøvHentSisteAttestering()
    fun prøvHentSisteAttestant(): NavIdentBruker.Attestant? = prøvHentSisteAttestering()?.attestant
}
