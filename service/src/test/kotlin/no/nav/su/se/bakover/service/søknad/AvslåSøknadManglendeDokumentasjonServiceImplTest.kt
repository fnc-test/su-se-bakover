package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.Dokumenttilstand
import dokument.domain.brev.BrevService
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.extensions.endOfMonth
import no.nav.su.se.bakover.common.extensions.startOfMonth
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.oppdaterSøknadsbehandling
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshistorikk
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksattSøknadsbehandlingResponse
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.IverksattAvslåttSøknadsbehandlingResponse
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.AvslåManglendeDokumentasjonCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.KunneIkkeAvslåSøknad
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOpplysningsplikt
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySøknadsbehandlingUtenStønadsperiode
import no.nav.su.se.bakover.test.nySøknadsbehandlingshendelse
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulering.simulerUtbetaling
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.søknad.nySøknadPåEksisterendeSak
import no.nav.su.se.bakover.test.søknad.søknadId
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandlingUføre
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDate

internal class AvslåSøknadManglendeDokumentasjonServiceImplTest {

    @Test
    fun `kan avslå en søknad uten påbegynt behandling`() {
        // Vi får testet alt minus sideeffektene (som skjer i IverksettSøknadsbehandlingService og testes isolert sett der.)
        val (sak, uavklart) = nySøknadsbehandlingUtenStønadsperiode(sakOgSøknad = nySakMedjournalførtSøknadOgOppgave())

        val mockedDokument = Dokument.UtenMetadata.Vedtak(
            opprettet = fixedTidspunkt,
            tittel = "testTittel",
            generertDokument = PdfA("testData".toByteArray()),
            generertDokumentJson = """{"test":"data"}""",
        )
        AvslåSøknadServiceAndMocks(
            sakService = mock {
                on { hentSakForSøknad(any()) } doReturn sak.right()
            },
            utbetalingService = mock {
                doAnswer { invocation ->
                    simulerUtbetaling(
                        sak,
                        invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                        invocation.getArgument(1) as Periode,
                        fixedClock,
                    )
                }.whenever(it).simulerUtbetaling(any(), any())
            },
            brevService = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn mockedDokument.right()
            },
        ).let { serviceAndMocks ->
            val actualSak = serviceAndMocks.service.avslå(
                AvslåManglendeDokumentasjonCommand(
                    uavklart.søknad.id,
                    saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlerSomAvslo"),
                    fritekstTilBrev = "fritekstTilBrev",
                ),
            ).getOrFail()

            val expectedPeriode = Periode.create(
                fraOgMed = LocalDate.now(serviceAndMocks.clock).startOfMonth(),
                tilOgMed = LocalDate.now(serviceAndMocks.clock).endOfMonth(),
            )

            val expectedSaksbehandler = NavIdentBruker.Saksbehandler("saksbehandlerSomAvslo")
            val expectedSøknadsbehandling = IverksattSøknadsbehandling.Avslag.UtenBeregning(
                id = uavklart.id,
                opprettet = uavklart.opprettet,
                sakId = uavklart.sakId,
                saksnummer = uavklart.saksnummer,
                søknad = uavklart.søknad,
                oppgaveId = uavklart.oppgaveId,
                fnr = uavklart.fnr,
                saksbehandler = expectedSaksbehandler,
                attesteringer = Attesteringshistorikk.create(
                    listOf(
                        Attestering.Iverksatt(
                            attestant = NavIdentBruker.Attestant("saksbehandlerSomAvslo"),
                            opprettet = fixedTidspunkt,
                        ),
                    ),
                ),
                fritekstTilBrev = "fritekstTilBrev",
                aldersvurdering = Aldersvurdering.SkalIkkeVurderes(Stønadsperiode.create(expectedPeriode)),
                grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling(
                    grunnlagsdata = uavklart.grunnlagsdata,
                    vilkårsvurderinger = uavklart.vilkårsvurderinger.oppdaterVilkår(
                        OpplysningspliktVilkår.Vurdert.tryCreate(
                            vurderingsperioder = nonEmptyListOf(
                                VurderingsperiodeOpplysningsplikt.create(
                                    id = (actualSak.søknadsbehandlinger[0].vilkårsvurderinger.opplysningsplikt as OpplysningspliktVilkår.Vurdert).vurderingsperioder[0].id,
                                    opprettet = Tidspunkt.now(fixedClock),
                                    grunnlag = Opplysningspliktgrunnlag(
                                        id = (actualSak.søknadsbehandlinger[0].vilkårsvurderinger.opplysningsplikt).grunnlag[0].id,
                                        opprettet = Tidspunkt.now(fixedClock),
                                        periode = expectedPeriode,
                                        beskrivelse = OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon,
                                    ),
                                    periode = expectedPeriode,
                                ),
                            ),
                        ).getOrFail(),
                    ),
                    eksterneGrunnlag = uavklart.eksterneGrunnlag,
                ),
                sakstype = sak.type,
                søknadsbehandlingsHistorikk = Søknadsbehandlingshistorikk.createFromExisting(
                    listOf(
                        nySøknadsbehandlingshendelse(
                            saksbehandler = saksbehandler,
                            handling = SøknadsbehandlingsHandling.StartetBehandling,
                        ),
                        nySøknadsbehandlingshendelse(
                            saksbehandler = expectedSaksbehandler,
                            handling = SøknadsbehandlingsHandling.OppdatertOpplysningsplikt,
                        ),
                    ),
                ),
            )

            val expectedVedtak = VedtakAvslagVilkår.createFromPersistence(
                id = actualSak.vedtakListe[0].id,
                opprettet = fixedTidspunkt,
                behandling = expectedSøknadsbehandling,
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlerSomAvslo"),
                attestant = NavIdentBruker.Attestant("saksbehandlerSomAvslo"),
                periode = expectedSøknadsbehandling.periode,
                avslagsgrunner = listOf(Avslagsgrunn.MANGLENDE_DOKUMENTASJON),
                dokumenttilstand = Dokumenttilstand.GENERERT,
            )

            val expectedSak = sak.oppdaterSøknadsbehandling(expectedSøknadsbehandling)
                .copy(
                    vedtakListe = listOf(expectedVedtak),
                )

            actualSak shouldBe expectedSak

            verify(serviceAndMocks.sakService).hentSakForSøknad(argThat { it shouldBe uavklart.søknad.id })

            verify(serviceAndMocks.iverksettSøknadsbehandlingService).iverksett(
                argThat<IverksattSøknadsbehandlingResponse<IverksattSøknadsbehandling.Avslag.UtenBeregning>> {
                    it shouldBe IverksattAvslåttSøknadsbehandlingResponse(
                        sak = expectedSak,
                        vedtak = expectedVedtak,
                        statistikkhendelse =
                        StatistikkEvent.Behandling.Søknad.Iverksatt.Avslag(
                            vedtak = expectedVedtak,
                        ),
                        dokument = Dokument.MedMetadata.Vedtak(
                            utenMetadata = mockedDokument,
                            metadata = Dokument.Metadata(
                                sakId = sak.id,
                                søknadId = null,
                                vedtakId = expectedVedtak.id,
                                revurderingId = null,
                                klageId = null,
                                journalpostId = null,
                                brevbestillingId = null,
                            ),

                        ),
                        oppgaveSomSkalLukkes = expectedSøknadsbehandling.oppgaveId,
                    )
                },
            )
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kan avslå en søknad med påbegynt behandling`() {
        val (sak, vilkårsvurdertInnvilget) = søknadsbehandlingVilkårsvurdertInnvilget()

        val mockedDokument = Dokument.UtenMetadata.Vedtak(
            opprettet = fixedTidspunkt,
            tittel = "testTittel",
            generertDokument = PdfA("testData".toByteArray()),
            generertDokumentJson = """{"test":"data"}""",
        )

        AvslåSøknadServiceAndMocks(
            sakService = mock {
                on { hentSakForSøknad(any()) } doReturn sak.right()
            },
            utbetalingService = mock {
                doAnswer { invocation ->
                    simulerUtbetaling(
                        sak,
                        invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                        invocation.getArgument(1) as Periode,
                        fixedClock,
                    )
                }.whenever(it).simulerUtbetaling(any(), any())
            },
            brevService = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn mockedDokument.right()
            },
        ).let { serviceAndMocks ->

            val actualSak = serviceAndMocks.service.avslå(
                AvslåManglendeDokumentasjonCommand(
                    søknadId,
                    saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlerSomAvslo"),
                    fritekstTilBrev = "fritekstTilBrev",
                ),
            ).getOrFail()

            val expectedPeriode = vilkårsvurdertInnvilget.periode
            val expectedSaksbehandler = NavIdentBruker.Saksbehandler("saksbehandlerSomAvslo")
            val expectedSøknadsbehandling = IverksattSøknadsbehandling.Avslag.UtenBeregning(
                id = vilkårsvurdertInnvilget.id,
                opprettet = vilkårsvurdertInnvilget.opprettet,
                sakId = vilkårsvurdertInnvilget.sakId,
                saksnummer = vilkårsvurdertInnvilget.saksnummer,
                søknad = vilkårsvurdertInnvilget.søknad,
                oppgaveId = vilkårsvurdertInnvilget.oppgaveId,
                fnr = vilkårsvurdertInnvilget.fnr,
                saksbehandler = expectedSaksbehandler,
                attesteringer = Attesteringshistorikk.create(
                    listOf(
                        Attestering.Iverksatt(
                            attestant = NavIdentBruker.Attestant("saksbehandlerSomAvslo"),
                            opprettet = fixedTidspunkt,
                        ),
                    ),
                ),
                fritekstTilBrev = "fritekstTilBrev",
                aldersvurdering = vilkårsvurdertInnvilget.aldersvurdering,
                grunnlagsdataOgVilkårsvurderinger = vilkårsvurdertInnvilget.grunnlagsdataOgVilkårsvurderinger.oppdaterVilkårsvurderinger(
                    vilkårsvurdertInnvilget.vilkårsvurderinger.oppdaterVilkår(
                        OpplysningspliktVilkår.Vurdert.tryCreate(
                            vurderingsperioder = nonEmptyListOf(
                                VurderingsperiodeOpplysningsplikt.create(
                                    id = (actualSak.søknadsbehandlinger[0].vilkårsvurderinger.opplysningsplikt as OpplysningspliktVilkår.Vurdert).vurderingsperioder[0].id,
                                    opprettet = Tidspunkt.now(fixedClock),
                                    grunnlag = Opplysningspliktgrunnlag(
                                        id = (actualSak.søknadsbehandlinger[0].vilkårsvurderinger.opplysningsplikt).grunnlag[0].id,
                                        opprettet = Tidspunkt.now(fixedClock),
                                        periode = expectedPeriode,
                                        beskrivelse = OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon,
                                    ),
                                    periode = expectedPeriode,
                                ),
                            ),
                        ).getOrFail(),
                    ),
                ),
                sakstype = sak.type,
                søknadsbehandlingsHistorikk = vilkårsvurdertInnvilget.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                    nonEmptyListOf(
                        nySøknadsbehandlingshendelse(
                            saksbehandler = expectedSaksbehandler,
                            handling = SøknadsbehandlingsHandling.OppdatertOpplysningsplikt,
                        ),
                    ),
                ),
            )

            val expectedVedtak = VedtakAvslagVilkår.createFromPersistence(
                id = actualSak.vedtakListe[0].id,
                opprettet = fixedTidspunkt,
                behandling = expectedSøknadsbehandling,
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlerSomAvslo"),
                attestant = NavIdentBruker.Attestant("saksbehandlerSomAvslo"),
                periode = expectedSøknadsbehandling.periode,
                avslagsgrunner = listOf(Avslagsgrunn.MANGLENDE_DOKUMENTASJON),
                dokumenttilstand = Dokumenttilstand.GENERERT,
            )

            val expectedSak = sak.oppdaterSøknadsbehandling(expectedSøknadsbehandling)
                .copy(
                    vedtakListe = listOf(expectedVedtak),
                )

            verify(serviceAndMocks.sakService).hentSakForSøknad(argThat { it shouldBe vilkårsvurdertInnvilget.søknad.id })

            verify(serviceAndMocks.iverksettSøknadsbehandlingService).iverksett(
                argThat<IverksattSøknadsbehandlingResponse<IverksattSøknadsbehandling.Avslag.UtenBeregning>> {
                    it shouldBe IverksattAvslåttSøknadsbehandlingResponse(
                        sak = expectedSak,
                        vedtak = expectedVedtak,
                        statistikkhendelse =
                        StatistikkEvent.Behandling.Søknad.Iverksatt.Avslag(
                            vedtak = expectedVedtak,
                        ),
                        dokument = Dokument.MedMetadata.Vedtak(
                            utenMetadata = mockedDokument,
                            metadata = Dokument.Metadata(
                                sakId = sak.id,
                                søknadId = null,
                                vedtakId = expectedVedtak.id,
                                revurderingId = null,
                                klageId = null,
                                journalpostId = null,
                                brevbestillingId = null,
                            ),

                        ),
                        oppgaveSomSkalLukkes = expectedSøknadsbehandling.oppgaveId,
                    )
                },
            )
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil dersom ugyldig tilstand`() {
        val (sak, _) = søknadsbehandlingIverksattInnvilget()

        val serviceAndMocks = AvslåSøknadServiceAndMocks(
            sakService = mock {
                on { hentSakForSøknad(any()) } doReturn sak.right()
            },
        )
        assertThrows<IllegalArgumentException> {
            serviceAndMocks.service.avslå(
                AvslåManglendeDokumentasjonCommand(
                    søknadId,
                    saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlerSomAvslo"),
                    fritekstTilBrev = "fritekstTilBrev",
                ),
            )
        }.message shouldBe "Søknadsbehandling var ikke av typen KanOppdaterePeriodeGrunnlagVilkår ved avslag pga. manglende dokumentasjon. Actual: no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling.Innvilget"
        verify(serviceAndMocks.sakService).hentSakForSøknad(søknadId)
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `svarer med feil dersom vi ikke får opprettet behandling`() {
        // Legger på en ny søknad som det ikke skal være lov å starte på samtidig som den andre søknadsbehandlingen.
        val (sak, nySøknad) = nySøknadPåEksisterendeSak(
            eksisterendeSak = vilkårsvurdertSøknadsbehandlingUføre().first,
        )

        AvslåSøknadServiceAndMocks(
            sakService = mock {
                on { hentSakForSøknad(any()) } doReturn sak.right()
            },
            clock = fixedClock,
        ).let {
            it.service.avslå(
                AvslåManglendeDokumentasjonCommand(
                    søknadId = nySøknad.id,
                    saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlerSomAvslo"),
                    fritekstTilBrev = "fritekstTilBrev",
                ),
            ) shouldBe KunneIkkeAvslåSøknad.KunneIkkeOppretteSøknadsbehandling(
                Sak.KunneIkkeOppretteSøknadsbehandling.HarÅpenSøknadsbehandling,
            ).left()

            verify(it.sakService).hentSakForSøknad(nySøknad.id)
            it.verifyNoMoreInteractions()
        }
    }

    private data class AvslåSøknadServiceAndMocks(
        val clock: Clock = fixedClock,
        val iverksettSøknadsbehandlingService: IverksettSøknadsbehandlingService = mock(),
        val sakService: SakService = mock(),
        val satsFactory: SatsFactory = satsFactoryTestPåDato(),
        val utbetalingService: UtbetalingService = mock(),
        val brevService: BrevService = mock(),
    ) {
        val service = AvslåSøknadManglendeDokumentasjonServiceImpl(
            clock = clock,
            sakService = sakService,
            satsFactory = satsFactory,
            iverksettSøknadsbehandlingService = iverksettSøknadsbehandlingService,
            utbetalingService = utbetalingService,
            brevService = brevService,
        )

        fun verifyNoMoreInteractions() {
            verifyNoMoreInteractions(
                sakService,
                iverksettSøknadsbehandlingService,
            )
        }
    }
}
