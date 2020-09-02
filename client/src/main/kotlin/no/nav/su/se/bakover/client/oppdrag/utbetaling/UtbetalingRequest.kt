package no.nav.su.se.bakover.client.oppdrag.utbetaling

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

// Ref: https://github.com/navikt/tjenestespesifikasjoner/blob/master/nav-virksomhet-oppdragsbehandling-v1-meldingsdefinisjon/src/main/xsd/no/trygdeetaten/skjema/oppdrag/oppdragskjema-1.xsd
@JacksonXmlRootElement(localName = "Oppdrag")
data class UtbetalingRequest(
    @field:JacksonXmlProperty(localName = "oppdrag-110")
    val oppdrag: Oppdrag
) {

    data class Oppdrag(
        val kodeAksjon: KodeAksjon,
        val kodeEndring: KodeEndring,
        /**  [1-8] tegn */
        val kodeFagomraade: String,
        /**  Maks 30 tegn */
        val fagsystemId: String,
        val utbetFrekvens: Utbetalingsfrekvens,
        /** Fødselsnummer eller Organisasjonsnummer [9,11] tegn */
        val oppdragGjelderId: String,
        /** xsd:date */
        val datoOppdragGjelderFom: String,
        /**  Maks 8 tegn */
        val saksbehId: String,
        /** minOccurs="0" i XSDen, men påkrevd her. */
        @field:JacksonXmlProperty(localName = "avstemming-115")
        val avstemming: Avstemming,
        @field:JacksonXmlProperty(localName = "oppdrags-enhet-120")
        @JacksonXmlElementWrapper(useWrapping = false)
        val oppdragsEnheter: List<OppdragsEnhet>,
        @field:JacksonXmlProperty(localName = "oppdrags-linje-150")
        val oppdragslinjer: List<Oppdragslinje>
    )

    enum class KodeAksjon(@JsonValue val value: Int) {
        EN(1),
        TRE(3);

        override fun toString() = value.toString()
    }

    enum class KodeEndring(@JsonValue val value: String) {
        NY("NY"),
        ENDRING("ENDR"),
        UENDRET("UEND");

        override fun toString() = value
    }

    enum class Utbetalingsfrekvens(@JsonValue val value: String) {
        DAG("DAG"),
        UKE("UKE"),
        MND("MND"),
        FJORTEN_DAGER("14DG"),
        ENGANGSUTBETALING("ENG");
    }

    data class OppdragsEnhet(
        /** [1,4] tegn */
        val typeEnhet: String,
        /** (tknr evnt orgnr+avd) [4,13] tegn */
        val enhet: String,
        val datoEnhetFom: String,
    )

    data class Avstemming(
        /** Makslengde 8 tegn */
        val kodeKomponent: String,
        /** Brukes for å identifisere data som skal avstemmes. Makslengde 30 tegn */
        val nokkelAvstemming: String,
        /** yyyy-MM-dd-HH.mm.ss.SSSSSS - makslengde 26 tegn */
        val tidspktMelding: String,
    )

    data class Oppdragslinje(
        val kodeEndringLinje: KodeEndringLinje,
        /** Makslengde 30 */
        val delytelseId: String,
        /** [1,50] tegn */
        val kodeKlassifik: String,
        val datoVedtakFom: String,
        val datoVedtakTom: String,
        /**
         * <xsd:totalDigits value="13"/>
         * <xsd:fractionDigits value="2"/>
         * xsd:decimal
         * */
        val sats: String,
        val fradragTillegg: FradragTillegg,
        val typeSats: TypeSats,
        /** Lengde 1 tegn */
        val brukKjoreplan: String,
        /** saksbehandlerId - Makslengde 8 tegn */
        val saksbehId: String,
        /** Fødselsnummer eller Organisasjonsnummer [9,11] tegn */
        val utbetalesTilId: String,
        /** Makslengde 30 tegn */
        val refDelytelseId: String?,

    ) {
        enum class KodeEndringLinje(@JsonValue val value: String) {
            NY("NY"),
            ENDRING("ENDR");

            override fun toString() = value
        }

        enum class FradragTillegg(@JsonValue val value: String) {
            FRADRAG("F"),
            TILLEGG("T");

            override fun toString() = value
        }

        enum class TypeSats(@JsonValue val value: String) {
            DAG("DAG"),
            UKE("UKE"),
            /** sic */
            FJORTEN_DB("14DB"),
            MND("MND"),
            AAR("AAR"),
            ENGANGSUTBETALING("ENG"),
            AKTO("AKTO");
        }
    }
}
