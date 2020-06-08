package no.nav.su.se.bakover.kafka

import io.ktor.http.HttpHeaders
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.meldinger.kafka.Topics
import no.nav.su.meldinger.kafka.soknad.NySøknad
import no.nav.su.se.bakover.ContextHolder
import no.nav.su.se.bakover.Fødselsnummer
import no.nav.su.se.bakover.PersonOppslag
import no.nav.su.se.bakover.SøknadObserver
import no.nav.su.se.bakover.SøknadObserver.SøknadMottattEvent
import org.apache.kafka.clients.producer.KafkaProducer

@KtorExperimentalAPI
internal class SøknadMottattEmitter(
        private val kafka: KafkaProducer<String, String>,
        private val personClient: PersonOppslag
) : SøknadObserver {
    override fun søknadMottatt(event: SøknadMottattEvent) {
        val aktørId = personClient.aktørId(Fødselsnummer(event.søknadInnhold.personopplysninger.fnr))
        kafka.send(event.somNySøknad(aktørId).toProducerRecord(Topics.SØKNAD_TOPIC))
    }
}

private fun SøknadMottattEvent.somNySøknad(aktørId: String): NySøknad = NySøknad(
        correlationId = ContextHolder.getMdc(HttpHeaders.XCorrelationId),
        fnr = søknadInnhold.personopplysninger.fnr,
        sakId = "${sakId}",
        aktørId = aktørId,
        søknadId = "${søknadId}",
        søknad = søknadInnhold.toJson()
)
