package no.nav.su.se.bakover.domain.revurdering

import arrow.core.left
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.TidslinjeForUtbetalinger
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.domain.revurdering.beregning.KunneIkkeBeregneRevurdering
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandling
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.sakMedUteståendeAvkorting
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.stønadsperiode2022
import no.nav.su.se.bakover.test.søknad.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.vilkår.avslåttFormueVilkår
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.math.abs

internal class RevurderingBeregnTest {

    @Test
    fun `beregning gir opphør hvis vilkår ikke er oppfylt`() {
        opprettetRevurdering(
            vilkårOverrides = listOf(
                avslåttUførevilkårUtenGrunnlag(),
            ),
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = år(2021),
                    arbeidsinntekt = 5000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let {
                over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe true
                it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
                (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner(fixedClock) shouldBe listOf(Opphørsgrunn.UFØRHET)
            }
        }
    }

    @Test
    fun `beregning gir ikke opphør hvis vilkår er oppfylt`() {
        opprettetRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = år(2021),
                    arbeidsinntekt = 5000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let {
                over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe true
                it shouldBe beOfType<BeregnetRevurdering.Innvilget>()
            }
        }
    }

    @Test
    fun `beregning med beløpsendring større enn 10 prosent fører til endring`() {
        opprettetRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = år(2021),
                    arbeidsinntekt = 5000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let {
                over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe true
                it shouldBe beOfType<BeregnetRevurdering.Innvilget>()
            }
        }
    }

    @Test
    fun `beregning med beløpsendring mindre enn 10 prosent fører til endring - g regulering`() {
        opprettetRevurdering(
            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP.toString(),
                begrunnelse = "a",
            ),
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = år(2021),
                    arbeidsinntekt = 1000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let {
                over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe false
                it shouldBe beOfType<BeregnetRevurdering.Innvilget>()
            }
        }
    }

    @Test
    fun `beregning uten beløpsendring - g regulering`() {
        opprettetRevurdering(
            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP.toString(),
                begrunnelse = "a",
            ),
        ).let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let {
                over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe false
                it shouldBe beOfType<BeregnetRevurdering.Innvilget>()
            }
        }
    }

    @Test
    fun `beregning som fører til beløp lik 0 gir opphør - g regulering`() {
        opprettetRevurdering(
            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP.toString(),
                begrunnelse = "a",
            ),
        ).let { (sak, revurdering) ->
            revurdering.oppdaterFradragOgMarkerSomVurdert(
                fradragsgrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = revurdering.periode,
                        arbeidsinntekt = 350_000.0,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ).getOrFail().beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let {
                over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe true
                it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
                (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner(fixedClock) shouldBe listOf(Opphørsgrunn.FOR_HØY_INNTEKT)
            }
        }
    }

    @Test
    fun `beregning som fører til beløp lik 0 gir opphør`() {
        opprettetRevurdering().let { (sak, revurdering) ->
            revurdering.oppdaterFradragOgMarkerSomVurdert(
                fradragsgrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = revurdering.periode,
                        arbeidsinntekt = 350_000.0,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ).getOrFail().beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let {
                over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe true
                it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
                (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner(fixedClock) shouldBe listOf(Opphørsgrunn.FOR_HØY_INNTEKT)
            }
        }
    }

    @Test
    fun `beregning som fører til beløp under minstegrense gir opphør`() {
        opprettetRevurdering().let { (sak, revurdering) ->
            revurdering.oppdaterFradragOgMarkerSomVurdert(
                fradragsgrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = Periode.create(1.januar(2021), 30.april(2021)),
                        arbeidsinntekt = (satsFactoryTestPåDato().høyUføre(januar(2021)).satsForMånedAsDouble - 250),
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    fradragsgrunnlagArbeidsinntekt(
                        periode = Periode.create(1.mai(2021), 31.desember(2021)),
                        arbeidsinntekt = (satsFactoryTestPåDato().høyUføre(mai(2021)).satsForMånedAsDouble - 250),
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ).getOrFail().beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let {
                over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe true
                it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
                (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner(fixedClock) shouldBe listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE)
            }
        }
    }

    @Test
    fun `annullerer avkorting dersom utenlandsopphold revurderes fra opphør til innvilget`() {
        val clock = TikkendeKlokke(fixedClock)

        val (sak, _, opphør) = sakMedUteståendeAvkorting(
            clock = clock,
            stønadsperiode = stønadsperiode2021,
            revurderingsperiode = stønadsperiode2021.periode,
            // feilutbetaling for jan-apr
            utbetalingerKjørtTilOgMed = { 1.mai(2021) },
        )

        beregnetRevurdering(
            clock = clock,
            sakOgVedtakSomKanRevurderes = sak to opphør,
            vilkårOverrides = listOf(
                utenlandsoppholdInnvilget(
                    periode = stønadsperiode2021.periode,
                    opprettet = Tidspunkt.now(clock),
                ),
            ),
        ).also { (sak, beregnet) ->
            beregnet.shouldBeType<BeregnetRevurdering.Innvilget>().also {
                beregnet.avkorting.shouldBeType<AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående>().also {
                    it.avkortingsvarsel shouldBe sak.uteståendeAvkorting
                }
            }
        }
    }

    @Test
    fun `beregning med avkorting`() {
        val clock = TikkendeKlokke()

        val (sak, _) = sakMedUteståendeAvkorting(
            stønadsperiode = stønadsperiode2021,
            revurderingsperiode = stønadsperiode2021.periode,
            // feilutbetaling for jan-apr
            utbetalingerKjørtTilOgMed = { 1.mai(2021) },
            clock = clock,
        )

        val (medNyStønadsperiode, _, nyStønadsperiode) = iverksattSøknadsbehandling(
            clock = clock,
            stønadsperiode = stønadsperiode2022,
            sakOgSøknad = sak to nySøknadJournalførtMedOppgave(
                clock = clock,
                sakId = sak.id,
                fnr = sak.fnr,
            ),
        )

        opprettetRevurdering(
            revurderingsperiode = stønadsperiode2022.periode,
            sakOgVedtakSomKanRevurderes = medNyStønadsperiode to nyStønadsperiode as VedtakSomKanRevurderes,
            clock = clock,
        ).let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = clock,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = clock,
                ).getOrFail(),
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let { beregnet ->
                beregnet shouldBe beOfType<BeregnetRevurdering.Innvilget>()
                beregnet.beregning.getSumYtelse() shouldBeGreaterThan 0
                beregnet.beregning.getMånedsberegninger()[0].getSumYtelse() shouldBe 0
                beregnet.beregning.getMånedsberegninger()[0].getFradrag()
                    .filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
                    .sumOf { it.månedsbeløp } shouldBe 20946
                beregnet.beregning.getMånedsberegninger()[1].getSumYtelse() shouldBe 0
                beregnet.beregning.getMånedsberegninger()[2].getSumYtelse() shouldBe 0
                beregnet.beregning.getMånedsberegninger()[3].getSumYtelse() shouldBe 0
                beregnet.beregning.getFradrag()
                    .filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
                    .sumOf { it.månedsbeløp } shouldBe 4 * 20946

                beregnet.grunnlagsdata.fradragsgrunnlag shouldBe listOf(
                    Grunnlag.Fradragsgrunnlag.create(
                        id = beregnet.grunnlagsdata.fradragsgrunnlag[0].id,
                        opprettet = beregnet.grunnlagsdata.fradragsgrunnlag[0].opprettet,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.AvkortingUtenlandsopphold,
                            månedsbeløp = 20946.0,
                            periode = januar(2022),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    Grunnlag.Fradragsgrunnlag.create(
                        id = beregnet.grunnlagsdata.fradragsgrunnlag[1].id,
                        opprettet = beregnet.grunnlagsdata.fradragsgrunnlag[1].opprettet,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.AvkortingUtenlandsopphold,
                            månedsbeløp = 20946.0,
                            periode = februar(2022),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    Grunnlag.Fradragsgrunnlag.create(
                        id = beregnet.grunnlagsdata.fradragsgrunnlag[2].id,
                        opprettet = beregnet.grunnlagsdata.fradragsgrunnlag[2].opprettet,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.AvkortingUtenlandsopphold,
                            månedsbeløp = 20946.0,
                            periode = mars(2022),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    Grunnlag.Fradragsgrunnlag.create(
                        id = beregnet.grunnlagsdata.fradragsgrunnlag[3].id,
                        opprettet = beregnet.grunnlagsdata.fradragsgrunnlag[3].opprettet,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.AvkortingUtenlandsopphold,
                            månedsbeløp = 20946.0,
                            periode = april(2022),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                )
                beregnet.informasjonSomRevurderes shouldBe revurdering.informasjonSomRevurderes
            }
        }
    }

    @Test
    fun `tilpasser avkorting i forhold til andre fradrag`() {
        val expectedTotalAvkorting = 4 * 20946
        val arbeidsinntekt = 3750

        val clock = TikkendeKlokke()

        val (sak, _) = sakMedUteståendeAvkorting(
            stønadsperiode = stønadsperiode2021,
            revurderingsperiode = stønadsperiode2021.periode,
            // feilutbetaling for jan-apr
            utbetalingerKjørtTilOgMed = { 1.mai(2021) },
            clock = clock,
        )

        val (medNyStønadsperiode, _, nyStønadsperiode) = iverksattSøknadsbehandling(
            stønadsperiode = stønadsperiode2022,
            sakOgSøknad = sak to nySøknadJournalførtMedOppgave(
                clock = clock,
                sakId = sak.id,
                fnr = sak.fnr,
            ),
            clock = clock,
        )

        opprettetRevurdering(
            clock = clock,
            sakOgVedtakSomKanRevurderes = medNyStønadsperiode to nyStønadsperiode as VedtakSomKanRevurderes,
            revurderingsperiode = stønadsperiode2022.periode,
            grunnlagsdataOverrides = listOf(
                Grunnlag.Fradragsgrunnlag.create(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    fradrag = FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = arbeidsinntekt.toDouble(),
                        periode = stønadsperiode2022.periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ),
        ).let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = clock,
                gjeldendeVedtaksdata = sak.hentGjeldendeVedtaksdata(
                    periode = revurdering.periode,
                    clock = clock,
                ).getOrFail(),
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let { beregnet ->
                beregnet shouldBe beOfType<BeregnetRevurdering.Innvilget>()
                beregnet.beregning.getSumYtelse() shouldBeGreaterThan 0
                beregnet.beregning.getMånedsberegninger()[0].getSumYtelse() shouldBe 0
                beregnet.beregning.getMånedsberegninger()[0].getFradrag()
                    .filter { it.fradragstype == Fradragstype.Arbeidsinntekt }
                    .sumOf { it.månedsbeløp } shouldBe 3750
                beregnet.beregning.getMånedsberegninger()[0].getFradrag()
                    .filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
                    .sumOf { it.månedsbeløp } shouldBe 17196
                beregnet.beregning.getMånedsberegninger()[1].getSumYtelse() shouldBe 0
                beregnet.beregning.getMånedsberegninger()[2].getSumYtelse() shouldBe 0
                beregnet.beregning.getMånedsberegninger()[3].getSumYtelse() shouldBe 0
                beregnet.beregning.getMånedsberegninger()[4].getSumYtelse() shouldBe
                    (5 * satsFactoryTestPåDato().høyUføre(januar(2022)).satsForMånedAvrundet) - (5 * arbeidsinntekt) - (expectedTotalAvkorting)
                beregnet.beregning.getFradrag()
                    .filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
                    .sumOf { it.månedsbeløp } shouldBe expectedTotalAvkorting

                beregnet.grunnlagsdata.fradragsgrunnlag shouldBe listOf(
                    Grunnlag.Fradragsgrunnlag.create(
                        id = beregnet.grunnlagsdata.fradragsgrunnlag[0].id,
                        opprettet = beregnet.grunnlagsdata.fradragsgrunnlag[0].opprettet,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.AvkortingUtenlandsopphold,
                            månedsbeløp = 17196.0,
                            periode = januar(2022),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    Grunnlag.Fradragsgrunnlag.create(
                        id = beregnet.grunnlagsdata.fradragsgrunnlag[1].id,
                        opprettet = beregnet.grunnlagsdata.fradragsgrunnlag[1].opprettet,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 3750.0,
                            periode = januar(2022)..desember(2022),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),

                    Grunnlag.Fradragsgrunnlag.create(
                        id = beregnet.grunnlagsdata.fradragsgrunnlag[2].id,
                        opprettet = beregnet.grunnlagsdata.fradragsgrunnlag[2].opprettet,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.AvkortingUtenlandsopphold,
                            månedsbeløp = 17196.0,
                            periode = februar(2022),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    Grunnlag.Fradragsgrunnlag.create(
                        id = beregnet.grunnlagsdata.fradragsgrunnlag[3].id,
                        opprettet = beregnet.grunnlagsdata.fradragsgrunnlag[3].opprettet,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.AvkortingUtenlandsopphold,
                            månedsbeløp = 17196.0,
                            periode = mars(2022),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    Grunnlag.Fradragsgrunnlag.create(
                        id = beregnet.grunnlagsdata.fradragsgrunnlag[4].id,
                        opprettet = beregnet.grunnlagsdata.fradragsgrunnlag[4].opprettet,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.AvkortingUtenlandsopphold,
                            månedsbeløp = 17196.0,
                            periode = april(2022),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    Grunnlag.Fradragsgrunnlag.create(
                        id = beregnet.grunnlagsdata.fradragsgrunnlag[5].id,
                        opprettet = beregnet.grunnlagsdata.fradragsgrunnlag[5].opprettet,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.AvkortingUtenlandsopphold,
                            månedsbeløp = 15000.0,
                            periode = mai(2022),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                )
                beregnet.informasjonSomRevurderes shouldBe revurdering.informasjonSomRevurderes
            }
        }
    }

    /**
     * Sjekker sperre for manglende funksjonalitet knyttet til opphør av ytelser som har løpende fradrag for avkorting.
     */
    @Test
    fun `får ikke lov til å opphøre dersom revurdering inneholder fremtidige fradrag for avkorting`() {
        val tikkendeKlokke = TikkendeKlokke(fixedClock)
        val (sak, _) = sakMedUteståendeAvkorting(
            clock = tikkendeKlokke,
            stønadsperiode = stønadsperiode2021,
        )
        // konsumer utestående avkorting i ny stønadsperiode
        val (medNyStønadsperiode, _, nyStønadsperiode) = iverksattSøknadsbehandling(
            clock = tikkendeKlokke,
            stønadsperiode = stønadsperiode2022,
            sakOgSøknad = sak to nySøknadJournalførtMedOppgave(
                clock = tikkendeKlokke,
                sakId = sak.id,
                fnr = sak.fnr,
            ),
        )

        // revurdering av den nye stønadsperioden drar med seg fradrag for avkorting fra nyStønadsperiode
        opprettetRevurdering(
            revurderingsperiode = nyStønadsperiode.periode,
            sakOgVedtakSomKanRevurderes = medNyStønadsperiode to nyStønadsperiode as VedtakSomKanRevurderes,
            clock = tikkendeKlokke,
            vilkårOverrides = listOf(
                avslåttFormueVilkår(periode = år(2022)),
            ),
        ).let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = tikkendeKlokke,
                gjeldendeVedtaksdata = sak.hentGjeldendeVedtaksdata(
                    periode = revurdering.periode,
                    clock = tikkendeKlokke,
                ).getOrFail(),
                satsFactory = satsFactoryTestPåDato(),
            ) shouldBe KunneIkkeBeregneRevurdering.OpphørAvYtelseSomSkalAvkortes.left()
        }
    }

    private fun over10ProsentEndring(
        nyBeregning: Beregning,
        eksisterendeUtbetalinger: Utbetalinger,
    ): Boolean {
        val nyBeregningBeløp = nyBeregning.getSumYtelse()
        val eksisterendeBeløp = eksisterendeUtbetalinger.sumOf {
            TidslinjeForUtbetalinger.fra(
                utbetalinger = eksisterendeUtbetalinger,
            )!!.sumOf { it.beløp * it.periode.getAntallMåneder() }
        }
        return abs((nyBeregningBeløp.toDouble() - eksisterendeBeløp) / eksisterendeBeløp * 100) > 10.0
    }
}
