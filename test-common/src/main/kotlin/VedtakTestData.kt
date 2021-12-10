package no.nav.su.se.bakover.test

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.singleFullstendigOrThrow
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Ikke journalført eller distribuert brev.
 * Oversendt utbetaling med kvittering.
 */
fun vedtakSøknadsbehandlingIverksattInnvilget(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerInnvilget(stønadsperiode.periode),
    utbetalingId: UUID30 = UUID30.randomUUID(),
    clock: Clock = fixedClock,
): Pair<Sak, Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling> {
    return søknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
    ).let { (sak, søknadsbehandling) ->
        val utbetaling = oversendtUtbetalingMedKvittering(
            id = utbetalingId,
            periode = stønadsperiode.periode,
            fnr = fnr,
            sakId = sakId,
            saksnummer = saksnummer,
            eksisterendeUtbetalinger = sak.utbetalinger,
            clock = clock,
        )
        val vedtak = Vedtak.fromSøknadsbehandling(
            søknadsbehandling = søknadsbehandling,
            utbetalingId = utbetalingId,
            clock = clock,
        )
        Pair(
            sak.copy(
                vedtakListe = nonEmptyListOf(vedtak),
                utbetalinger = nonEmptyListOf(utbetaling),
            ),
            vedtak,
        )
    }
}

/**
 * Ikke journalført eller distribuert brev
 */
fun vedtakSøknadsbehandlingIverksattAvslagMedBeregning(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerInnvilget(
        periode = stønadsperiode.periode,
        uføre = innvilgetUførevilkårForventetInntekt0(
            id = UUID.randomUUID(),
            periode = stønadsperiode.periode,
            uføregrunnlag = uføregrunnlagForventetInntekt(
                periode = stønadsperiode.periode,
                forventetInntekt = 1_000_000,
            )
        ),
    ),
    clock: Clock = fixedClock,
): Pair<Sak, Vedtak.Avslag.AvslagBeregning> {
    return søknadsbehandlingIverksattAvslagMedBeregning(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
    ).let { (sak, søknadsbehandling) ->
        val vedtak = Vedtak.Avslag.fromSøknadsbehandlingMedBeregning(
            avslag = søknadsbehandling,
            clock = clock,
        )
        Pair(
            sak.copy(
                vedtakListe = sak.vedtakListe + vedtak,
            ),
            vedtak,
        )
    }
}

/**
 * Ikke journalført eller distribuert brev
 */
fun vedtakRevurderingIverksattInnvilget(
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger(
        grunnlagsdata = grunnlagsdataEnsligUtenFradrag(revurderingsperiode),
        vilkårsvurderinger = vilkårsvurderingerInnvilget(revurderingsperiode),
    ),
    utbetalingId: UUID30 = UUID30.randomUUID(),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes>,
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    clock: Clock = fixedClock,
): Pair<Sak, Vedtak.EndringIYtelse> {
    return iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        revurderingsårsak = revurderingsårsak,
    ).let { (sak, revurdering) ->
        val utbetaling = oversendtUtbetalingMedKvittering(
            saksnummer = saksnummer,
            sakId = sakId,
            fnr = fnr,
            id = utbetalingId,
            eksisterendeUtbetalinger = sak.utbetalinger,
            clock = clock,
        )
        val vedtak = Vedtak.from(
            revurdering = revurdering,
            utbetalingId = utbetalingId,
            clock = clock,
        )
        Pair(
            sak.copy(
                vedtakListe = sak.vedtakListe + vedtak,
                utbetalinger = sak.utbetalinger + utbetaling,
            ),
            vedtak,
        )
    }
}

/**
 * @param sakOgVedtakSomKanRevurderes dersom denne ikke sendes inn vil clock bli satt til +0 sekunder på søknadsbehandlingsvedtaket og +1 på stansvedtaket
 */
fun vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
    clock: Clock = fixedClock,
    periode: Periode = Periode.create(
        fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        tilOgMed = periode2021.tilOgMed,
    ),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        stønadsperiode = Stønadsperiode.create(periode, "whatever"),
        clock = clock,
    ),
    attestering: Attestering = attesteringIverksatt,
): Pair<Sak, Vedtak.EndringIYtelse.StansAvYtelse> {
    return iverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        periode = periode,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        attestering = attestering,
        clock = clock.plus(1, ChronoUnit.SECONDS),
    ).let { (sak, revurdering) ->
        val utbetaling = oversendtStansUtbetalingUtenKvittering(
            stansDato = revurdering.periode.fraOgMed,
            fnr = sak.fnr,
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            eksisterendeUtbetalinger = sak.utbetalinger,
            clock = clock.plus(1, ChronoUnit.SECONDS),
        )

        val vedtak = Vedtak.from(
            revurdering = revurdering,
            utbetalingId = utbetaling.id,
            clock = clock.plus(1, ChronoUnit.SECONDS),
        )

        sak.copy(
            vedtakListe = sak.vedtakListe + vedtak,
            utbetalinger = sak.utbetalinger + utbetaling,
        ) to vedtak
    }
}

/**
 * @param sakOgVedtakSomKanRevurderes Hvis denne ikke sendes inn lages det 2 vedtak med clock+0 og clock+1
 *
 * [GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse] vil bruke clock+2
 */
fun vedtakIverksattGjenopptakAvYtelseFraIverksattStans(
    periode: Periode,
    clock: Clock = fixedClock,
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        periode = periode,
        clock = clock,
    ),
    attestering: Attestering = attesteringIverksatt,
): Pair<Sak, Vedtak.EndringIYtelse.GjenopptakAvYtelse> {
    return iverksattGjenopptakelseAvYtelseFraVedtakStansAvYtelse(
        periode = periode,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        attestering = attestering,
        // funksjonen vil legge på clock +2
        clock = clock,
    ).let { (sak, revurdering) ->
        val utbetaling = oversendtGjenopptakUtbetalingUtenKvittering(
            fnr = sak.fnr,
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            eksisterendeUtbetalinger = sak.utbetalinger,
            clock = clock,
        )

        val vedtak = Vedtak.from(
            revurdering = revurdering,
            utbetalingId = utbetaling.id,
            clock = clock,
        )

        sak.copy(
            vedtakListe = sak.vedtakListe + vedtak,
            utbetalinger = sak.utbetalinger + utbetaling,
        ) to vedtak
    }
}

fun vedtakRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    clock: Clock = fixedClock,
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    ),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
        periode = revurderingsperiode,
        clock = clock,
    ).let {
        it.copy(
            vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgAndreInnvilget(
                periode = revurderingsperiode,
                bosituasjon = it.grunnlagsdata.bosituasjon.singleFullstendigOrThrow(),
            ),
        )
    },
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    attestering: Attestering.Iverksatt = attesteringIverksatt,
): Pair<Sak, Vedtak.EndringIYtelse.OpphørtRevurdering> {
    return iverksattRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
        revurderingsårsak = revurderingsårsak,
        attestering = attestering,
        clock = clock,
    ).let { (sak, revurdering) ->

        val utbetaling = oversendtUtbetalingMedKvittering(
            periode = stønadsperiode.periode,
            fnr = sak.fnr,
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            eksisterendeUtbetalinger = sak.utbetalinger,
            clock = clock,
        )

        val vedtak = Vedtak.from(
            revurdering = revurdering,
            utbetalingId = utbetaling.id,
            clock = clock,
        )

        sak.copy(
            vedtakListe = sak.vedtakListe + vedtak,
            utbetalinger = sak.utbetalinger + utbetaling,
        ) to vedtak
    }
}

val journalpostIdVedtak = JournalpostId("journalpostIdVedtak")
val brevbestillingIdVedtak = BrevbestillingId("brevbestillingIdVedtak")
val journalført: JournalføringOgBrevdistribusjon.Journalført = JournalføringOgBrevdistribusjon.Journalført(
    journalpostId = journalpostIdVedtak,
)
val journalførtOgDistribuertBrev: JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev =
    JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
        journalpostId = journalpostIdVedtak,
        brevbestillingId = brevbestillingIdVedtak,
    )
