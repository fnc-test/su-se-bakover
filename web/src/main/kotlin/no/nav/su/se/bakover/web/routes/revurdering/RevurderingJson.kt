package no.nav.su.se.bakover.web.routes.revurdering

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.web.routes.grunnlag.revurdering.GrunnlagsdataRevurderingJson
import no.nav.su.se.bakover.web.routes.grunnlag.revurdering.toRevurderingJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.AttesteringJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingsinformasjonJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingsinformasjonJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.UnderkjennelseJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.BeregningJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.toJson
import java.time.format.DateTimeFormatter

sealed class RevurderingJson

internal enum class RevurderingsStatus {
    OPPRETTET,
    BEREGNET_INNVILGET,
    BEREGNET_OPPHØRT,
    BEREGNET_INGEN_ENDRING,
    SIMULERT_INNVILGET,
    SIMULERT_OPPHØRT,
    TIL_ATTESTERING_INNVILGET,
    TIL_ATTESTERING_OPPHØRT,
    TIL_ATTESTERING_INGEN_ENDRING,
    IVERKSATT_INNVILGET,
    IVERKSATT_OPPHØRT,
    IVERKSATT_INGEN_ENDRING,
    UNDERKJENT_INNVILGET,
    UNDERKJENT_OPPHØRT,
    UNDERKJENT_INGEN_ENDRING
}

internal data class RevurdertBeregningJson(
    val beregning: BeregningJson,
    val revurdert: BeregningJson,
)

internal data class OpprettetRevurderingJson(
    val id: String,
    val opprettet: String,
    val periode: PeriodeJson,
    val tilRevurdering: VedtakJson,
    val saksbehandler: String,
    val fritekstTilBrev: String,
    val årsak: String,
    val begrunnelse: String,
    val behandlingsinformasjon: BehandlingsinformasjonJson,
    val grunnlag: GrunnlagsdataRevurderingJson,
) : RevurderingJson() {
    @JsonInclude
    val status = RevurderingsStatus.OPPRETTET
}

internal data class BeregnetRevurderingJson(
    val id: String,
    val opprettet: String,
    val periode: PeriodeJson,
    val tilRevurdering: VedtakJson,
    val beregninger: RevurdertBeregningJson,
    val saksbehandler: String,
    val fritekstTilBrev: String,
    val årsak: String,
    val begrunnelse: String,
    val status: RevurderingsStatus,
    val behandlingsinformasjon: BehandlingsinformasjonJson,
    val grunnlag: GrunnlagsdataRevurderingJson,
) : RevurderingJson()

internal data class SimulertRevurderingJson(
    val id: String,
    val opprettet: String,
    val periode: PeriodeJson,
    val tilRevurdering: VedtakJson,
    val beregninger: RevurdertBeregningJson,
    val saksbehandler: String,
    val fritekstTilBrev: String,
    val årsak: String,
    val begrunnelse: String,
    val status: RevurderingsStatus,
    val behandlingsinformasjon: BehandlingsinformasjonJson,
    val simulering: SimuleringJson,
    val grunnlag: GrunnlagsdataRevurderingJson,
) : RevurderingJson()

internal data class TilAttesteringJson(
    val id: String,
    val opprettet: String,
    val periode: PeriodeJson,
    val tilRevurdering: VedtakJson,
    val beregninger: RevurdertBeregningJson,
    val saksbehandler: String,
    val fritekstTilBrev: String,
    val skalFøreTilBrevutsending: Boolean,
    val årsak: String,
    val begrunnelse: String,
    val status: RevurderingsStatus,
    val behandlingsinformasjon: BehandlingsinformasjonJson,
    val simulering: SimuleringJson?,
    val grunnlag: GrunnlagsdataRevurderingJson,
) : RevurderingJson()

internal data class IverksattRevurderingJson(
    val id: String,
    val opprettet: String,
    val periode: PeriodeJson,
    val tilRevurdering: VedtakJson,
    val beregninger: RevurdertBeregningJson,
    val saksbehandler: String,
    val fritekstTilBrev: String,
    val skalFøreTilBrevutsending: Boolean,
    val årsak: String,
    val begrunnelse: String,
    val attestant: String,
    val status: RevurderingsStatus,
    val behandlingsinformasjon: BehandlingsinformasjonJson,
    val simulering: SimuleringJson?,
    val grunnlag: GrunnlagsdataRevurderingJson,
) : RevurderingJson()

internal data class UnderkjentRevurderingJson(
    val id: String,
    val opprettet: String,
    val periode: PeriodeJson,
    val tilRevurdering: VedtakJson,
    val beregninger: RevurdertBeregningJson,
    val saksbehandler: String,
    val fritekstTilBrev: String,
    val skalFøreTilBrevutsending: Boolean,
    val årsak: String,
    val begrunnelse: String,
    val attestering: AttesteringJson,
    val status: RevurderingsStatus,
    val behandlingsinformasjon: BehandlingsinformasjonJson,
    val simulering: SimuleringJson?,
    val grunnlag: GrunnlagsdataRevurderingJson,
) : RevurderingJson()

internal fun Revurdering.toJson(): RevurderingJson = when (this) {
    is OpprettetRevurdering -> OpprettetRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        behandlingsinformasjon = behandlingsinformasjon.toJson(),
        grunnlag = grunnlagsdata.toRevurderingJson(),
    )
    is SimulertRevurdering -> SimulertRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning.toJson(),
            revurdert = beregning.toJson(),
        ),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        status = InstansTilStatusMapper(this).status,
        behandlingsinformasjon = behandlingsinformasjon.toJson(),
        simulering = simulering.toJson(),
        grunnlag = grunnlagsdata.toRevurderingJson(),
    )
    is RevurderingTilAttestering -> TilAttesteringJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning.toJson(),
            revurdert = beregning.toJson(),
        ),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        skalFøreTilBrevutsending = when (this) {
            is RevurderingTilAttestering.IngenEndring -> skalFøreTilBrevutsending
            is RevurderingTilAttestering.Innvilget -> true
            is RevurderingTilAttestering.Opphørt -> true
        },
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        status = InstansTilStatusMapper(this).status,
        behandlingsinformasjon = behandlingsinformasjon.toJson(),
        simulering = when (this) {
            is RevurderingTilAttestering.IngenEndring -> null
            is RevurderingTilAttestering.Innvilget -> simulering.toJson()
            is RevurderingTilAttestering.Opphørt -> simulering.toJson()
        },
        grunnlag = grunnlagsdata.toRevurderingJson(),
    )
    is IverksattRevurdering -> IverksattRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning.toJson(),
            revurdert = beregning.toJson(),
        ),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        skalFøreTilBrevutsending = when (this) {
            is IverksattRevurdering.IngenEndring -> skalFøreTilBrevutsending
            is IverksattRevurdering.Innvilget -> true
            is IverksattRevurdering.Opphørt -> true
        },
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        attestant = attestering.attestant.toString(),
        status = InstansTilStatusMapper(this).status,
        behandlingsinformasjon = behandlingsinformasjon.toJson(),
        simulering = when (this) {
            is IverksattRevurdering.IngenEndring -> null
            is IverksattRevurdering.Innvilget -> simulering.toJson()
            is IverksattRevurdering.Opphørt -> simulering.toJson()
        },
        grunnlag = grunnlagsdata.toRevurderingJson(),
    )
    is UnderkjentRevurdering -> UnderkjentRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning.toJson(),
            revurdert = beregning.toJson(),
        ),
        saksbehandler = saksbehandler.toString(),
        attestering = AttesteringJson(
            attestant = attestering.attestant.navIdent,
            underkjennelse = UnderkjennelseJson(
                grunn = attestering.grunn.toString(),
                kommentar = attestering.kommentar,
            ),
        ),
        fritekstTilBrev = fritekstTilBrev,
        skalFøreTilBrevutsending = when (this) {
            is UnderkjentRevurdering.IngenEndring -> skalFøreTilBrevutsending
            is UnderkjentRevurdering.Innvilget -> true
            is UnderkjentRevurdering.Opphørt -> true
        },
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        status = InstansTilStatusMapper(this).status,
        behandlingsinformasjon = behandlingsinformasjon.toJson(),
        simulering = when (this) {
            is UnderkjentRevurdering.IngenEndring -> null
            is UnderkjentRevurdering.Innvilget -> simulering.toJson()
            is UnderkjentRevurdering.Opphørt -> simulering.toJson()
        },
        grunnlag = grunnlagsdata.toRevurderingJson(),
    )
    is BeregnetRevurdering -> BeregnetRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        periode = periode.toJson(),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning.toJson(),
            revurdert = beregning.toJson(),
        ),
        saksbehandler = saksbehandler.toString(),
        fritekstTilBrev = fritekstTilBrev,
        årsak = revurderingsårsak.årsak.toString(),
        begrunnelse = revurderingsårsak.begrunnelse.toString(),
        status = InstansTilStatusMapper(this).status,
        behandlingsinformasjon = behandlingsinformasjon.toJson(),
        grunnlag = grunnlagsdata.toRevurderingJson(),
    )
}

internal class InstansTilStatusMapper(revurdering: Revurdering) {
    val status = when (revurdering) {
        is BeregnetRevurdering.IngenEndring -> RevurderingsStatus.BEREGNET_INGEN_ENDRING
        is BeregnetRevurdering.Innvilget -> RevurderingsStatus.BEREGNET_INNVILGET
        is BeregnetRevurdering.Opphørt -> RevurderingsStatus.BEREGNET_OPPHØRT
        is IverksattRevurdering.IngenEndring -> RevurderingsStatus.IVERKSATT_INGEN_ENDRING
        is IverksattRevurdering.Innvilget -> RevurderingsStatus.IVERKSATT_INNVILGET
        is IverksattRevurdering.Opphørt -> RevurderingsStatus.IVERKSATT_OPPHØRT
        is OpprettetRevurdering -> RevurderingsStatus.OPPRETTET
        is RevurderingTilAttestering.IngenEndring -> RevurderingsStatus.TIL_ATTESTERING_INGEN_ENDRING
        is RevurderingTilAttestering.Innvilget -> RevurderingsStatus.TIL_ATTESTERING_INNVILGET
        is RevurderingTilAttestering.Opphørt -> RevurderingsStatus.TIL_ATTESTERING_OPPHØRT
        is SimulertRevurdering.Innvilget -> RevurderingsStatus.SIMULERT_INNVILGET
        is SimulertRevurdering.Opphørt -> RevurderingsStatus.SIMULERT_OPPHØRT
        is UnderkjentRevurdering.IngenEndring -> RevurderingsStatus.UNDERKJENT_INGEN_ENDRING
        is UnderkjentRevurdering.Innvilget -> RevurderingsStatus.UNDERKJENT_INNVILGET
        is UnderkjentRevurdering.Opphørt -> RevurderingsStatus.UNDERKJENT_OPPHØRT
    }
}
