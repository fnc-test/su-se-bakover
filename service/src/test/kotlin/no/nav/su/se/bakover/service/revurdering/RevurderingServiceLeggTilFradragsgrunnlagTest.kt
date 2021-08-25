package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Vurderingstatus
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

class RevurderingServiceLeggTilFradragsgrunnlagTest {
    @Test
    fun `lagreFradrag happy case`() {
        val revurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurdering
        }

        val grunnlagServiceMock = mock<GrunnlagService>()

        val revurderingService = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            grunnlagService = grunnlagServiceMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = revurdering.id,
            fradragsrunnlag = listOf(
                Grunnlag.Fradragsgrunnlag(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 5000.0,
                        periode = revurdering.periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    opprettet = fixedTidspunkt,
                ),
            ),
        )

        revurderingService.leggTilFradragsgrunnlag(request).getOrHandle { fail { "uventet respons" } }

        verify(revurderingRepoMock).hent(argThat { it shouldBe revurdering.id })
        verify(grunnlagServiceMock).lagreFradragsgrunnlag(
            argThat { it shouldBe revurdering.id },
            argThat { it shouldBe request.fradragsrunnlag },
        )
        verify(revurderingRepoMock).lagre(
            argThat {
                it shouldBe revurdering.copy(
                    informasjonSomRevurderes = InformasjonSomRevurderes.create(
                        mapOf(Revurderingsteg.Inntekt to Vurderingstatus.Vurdert),
                    ),
                    grunnlagsdata = revurdering.grunnlagsdata.copy(
                        fradragsgrunnlag = nonEmptyListOf(
                            fradragsgrunnlagArbeidsinntekt(periode = revurdering.periode, arbeidsinntekt = 5000.0).copy(
                                id = it.grunnlagsdata.fradragsgrunnlag.first().id
                            ),
                        ),
                    ),
                )
            },
        )

        verifyNoMoreInteractions(revurderingRepoMock, grunnlagServiceMock)
    }

    @Test
    fun `lagreFradrag finner ikke revurdering`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn null
        }

        val grunnlagServiceMock = mock<GrunnlagService>()

        val revurderingService = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            grunnlagService = grunnlagServiceMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = revurderingId,
            fradragsrunnlag = listOf(
                Grunnlag.Fradragsgrunnlag(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 0.0,
                        periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    opprettet = fixedTidspunkt,
                ),
            ),
        )

        revurderingService.leggTilFradragsgrunnlag(
            request,
        ) shouldBe KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling.left()

        inOrder(
            revurderingRepoMock,
            grunnlagServiceMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        }

        verifyNoMoreInteractions(revurderingRepoMock, grunnlagServiceMock)
    }

    @Test
    fun `lagreFradrag har en status som gjør at man ikke kan legge til fradrag`() {

        val tidligereRevurdering = tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn tidligereRevurdering
        }

        val grunnlagServiceMock = mock<GrunnlagService>()

        val revurderingService = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            grunnlagService = grunnlagServiceMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = tidligereRevurdering.id,
            fradragsrunnlag = listOf(
                Grunnlag.Fradragsgrunnlag(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 0.0,
                        periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    opprettet = fixedTidspunkt,
                ),
            ),
        )

        revurderingService.leggTilFradragsgrunnlag(
            request,
        ) shouldBe KunneIkkeLeggeTilFradragsgrunnlag.UgyldigTilstand(
            fra = tidligereRevurdering::class,
            til = OpprettetRevurdering::class,
        ).left()

        verify(revurderingRepoMock).hent(argThat { it shouldBe tidligereRevurdering.id })

        verifyNoMoreInteractions(revurderingRepoMock, grunnlagServiceMock)
    }

    @Test
    fun `validerer fradragsgrunnlag`() {
        val revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021))
        val revurderingMock = mock<OpprettetRevurdering> {

            on { periode } doReturn revurderingsperiode
            on { grunnlagsdata } doReturn Grunnlagsdata(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = revurderingsperiode,
                        begrunnelse = null,
                    ),
                ),
            )
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurderingMock
        }

        val revurderingService = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = revurderingId,
            fradragsrunnlag = listOf(
                Grunnlag.Fradragsgrunnlag(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 0.0,
                        periode = revurderingsperiode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    opprettet = fixedTidspunkt,
                ),
                Grunnlag.Fradragsgrunnlag(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Kontantstøtte,
                        månedsbeløp = 0.0,
                        periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.juli(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    opprettet = fixedTidspunkt,
                ),
            ),
        )

        revurderingService.leggTilFradragsgrunnlag(
            request,
        ) shouldBe KunneIkkeLeggeTilFradragsgrunnlag.FradragsgrunnlagUtenforRevurderingsperiode.left()
    }
}
