package no.nav.su.se.bakover.domain.søknadsbehandling.opprett

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.innvilgetSøknadsbehandlingMedÅpenRegulering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandling
import no.nav.su.se.bakover.test.nySakUføre
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.søknad.nySøknadPåEksisterendeSak
import org.junit.jupiter.api.Test

internal class OpprettNySøknadsbehandlingTest {

    @Test
    fun `oppretter søknadsbehandling dersom det ikke finnes eksisterende åpne behandlinger`() {
        val (sak, søknad) = nySakUføre()
        sak.opprettNySøknadsbehandling(søknad.id, fixedClock, saksbehandler).shouldBeRight()
    }

    @Test
    fun `Kan ikke opprette mer enn 1 søknadsbehandling per søknad`() {
        val (sak, søknadsbehandling) = iverksattSøknadsbehandling()
        sak.opprettNySøknadsbehandling(
            søknadsbehandling.søknad.id,
            fixedClock,
            saksbehandler,
        ) shouldBe Sak.KunneIkkeOppretteSøknadsbehandling.FinnesAlleredeSøknadsehandlingForSøknad.left()
    }

    @Test
    fun `Kan ikke opprette søknadsbehandling dersom det finnes en åpen søknadsbehandling`() {
        val (sak, søknad) = nySøknadPåEksisterendeSak(
            eksisterendeSak = nySøknadsbehandlingMedStønadsperiode().first,
        )
        sak.opprettNySøknadsbehandling(
            søknad.id,
            fixedClock,
            saksbehandler,
        ) shouldBe Sak.KunneIkkeOppretteSøknadsbehandling.HarÅpenSøknadsbehandling.left()
    }

    @Test
    fun `Kan opprette søknadsbehandling dersom det finnes en åpen revurdering`() {
        val clock = TikkendeKlokke()
        val (sak, søknad) = nySøknadPåEksisterendeSak(
            eksisterendeSak = opprettetRevurdering(clock = clock).first,
            clock = clock,
        )
        sak.opprettNySøknadsbehandling(
            søknadId = søknad.id,
            clock = clock,
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    fun `Kan opprette søknadsbehandling dersom det finnes en åpen regulering `() {
        val clock = TikkendeKlokke()
        val (sak, søknad) = nySøknadPåEksisterendeSak(
            clock = clock,
            eksisterendeSak = innvilgetSøknadsbehandlingMedÅpenRegulering(
                mai(year = 2021),
                clock = clock,
            ).first,
        )
        sak.opprettNySøknadsbehandling(
            søknadId = søknad.id,
            clock = clock,
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }
}
