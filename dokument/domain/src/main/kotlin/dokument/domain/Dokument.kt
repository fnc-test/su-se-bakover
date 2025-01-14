package dokument.domain

import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

sealed interface Dokument {
    val id: UUID
    val opprettet: Tidspunkt
    val tittel: String
    val generertDokument: PdfA

    /**
     * Json-representasjon av data som ble benyttet for opprettelsen av [generertDokument]
     */
    val generertDokumentJson: String

    sealed class UtenMetadata : Dokument {

        abstract fun leggTilMetadata(metadata: Metadata): MedMetadata

        data class Vedtak(
            override val id: UUID = UUID.randomUUID(),
            override val opprettet: Tidspunkt,
            override val tittel: String,
            override val generertDokument: PdfA,
            override val generertDokumentJson: String,
        ) : UtenMetadata() {
            override fun leggTilMetadata(metadata: Metadata): MedMetadata.Vedtak {
                return MedMetadata.Vedtak(this, metadata)
            }
        }

        sealed class Informasjon : UtenMetadata() {
            data class Viktig(
                override val id: UUID = UUID.randomUUID(),
                override val opprettet: Tidspunkt,
                override val tittel: String,
                override val generertDokument: PdfA,
                override val generertDokumentJson: String,
            ) : Informasjon() {
                override fun leggTilMetadata(metadata: Metadata): MedMetadata.Informasjon.Viktig {
                    return MedMetadata.Informasjon.Viktig(this, metadata)
                }
            }

            data class Annet(
                override val id: UUID = UUID.randomUUID(),
                override val opprettet: Tidspunkt,
                override val tittel: String,
                override val generertDokument: PdfA,
                override val generertDokumentJson: String,
            ) : Informasjon() {
                override fun leggTilMetadata(metadata: Metadata): MedMetadata.Informasjon.Annet {
                    return MedMetadata.Informasjon.Annet(this, metadata)
                }
            }
        }
    }

    sealed class MedMetadata : Dokument {
        abstract val metadata: Metadata
        abstract val distribusjonstype: Distribusjonstype
        val distribusjonstidspunkt = Distribusjonstidspunkt.KJERNETID

        data class Vedtak(
            override val id: UUID = UUID.randomUUID(),
            override val opprettet: Tidspunkt,
            override val tittel: String,
            override val generertDokument: PdfA,
            override val generertDokumentJson: String,
            override val metadata: Metadata,
        ) : MedMetadata() {
            override val distribusjonstype = Distribusjonstype.VEDTAK

            constructor(utenMetadata: UtenMetadata, metadata: Metadata) : this(
                id = utenMetadata.id,
                opprettet = utenMetadata.opprettet,
                tittel = utenMetadata.tittel,
                generertDokument = utenMetadata.generertDokument,
                generertDokumentJson = utenMetadata.generertDokumentJson,
                metadata = metadata,
            )
        }

        /**
         * Typen informasjon vil bestemme når på døgnet brevet skal distribueres. Se DokdistFordelingClient
         */
        sealed class Informasjon : MedMetadata() {
            data class Viktig(
                override val id: UUID = UUID.randomUUID(),
                override val opprettet: Tidspunkt,
                override val tittel: String,
                override val generertDokument: PdfA,
                override val generertDokumentJson: String,
                override val metadata: Metadata,
            ) : Informasjon() {
                override val distribusjonstype = Distribusjonstype.VIKTIG

                constructor(utenMetadata: UtenMetadata, metadata: Metadata) : this(
                    id = utenMetadata.id,
                    opprettet = utenMetadata.opprettet,
                    tittel = utenMetadata.tittel,
                    generertDokument = utenMetadata.generertDokument,
                    generertDokumentJson = utenMetadata.generertDokumentJson,
                    metadata = metadata,
                )
            }

            data class Annet(
                override val id: UUID = UUID.randomUUID(),
                override val opprettet: Tidspunkt,
                override val tittel: String,
                override val generertDokument: PdfA,
                override val generertDokumentJson: String,
                override val metadata: Metadata,
            ) : Informasjon() {
                override val distribusjonstype = Distribusjonstype.ANNET

                constructor(utenMetadata: UtenMetadata, metadata: Metadata) : this(
                    id = utenMetadata.id,
                    opprettet = utenMetadata.opprettet,
                    tittel = utenMetadata.tittel,
                    generertDokument = utenMetadata.generertDokument,
                    generertDokumentJson = utenMetadata.generertDokumentJson,
                    metadata = metadata,
                )
            }
        }
    }

    /**
     * Du skal bruke en, og bare en knyttning til vedtak eller behandling
     * Dersom du har behov for å knytte et dokument til flere, da kreves det en del omskrivning
     */
    data class Metadata(
        val sakId: UUID,
        /**
         * Denne er for selve søknaden, og ikke behandlingen.
         * Dokumenter for behandlingen baserer seg bare på utkast. Deretter når dem gjøres om til vedtak, brukes vedtakId
         */
        val søknadId: UUID? = null,
        val vedtakId: UUID? = null,
        val revurderingId: UUID? = null,
        val klageId: UUID? = null,
        val tilbakekrevingsbehandlingId: UUID? = null,
        val journalpostId: String? = null,
        val brevbestillingId: String? = null,
    )
}
