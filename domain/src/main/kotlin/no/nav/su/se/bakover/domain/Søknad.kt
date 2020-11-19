package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad.Lukket.LukketType
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import java.util.UUID

sealed class Søknad {
    abstract val sakId: UUID
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val søknadInnhold: SøknadInnhold

    abstract fun lukk(
        lukketAv: Saksbehandler,
        type: LukketType
    ): Lukket

    data class Ny(
        override val sakId: UUID,
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt = now(),
        override val søknadInnhold: SøknadInnhold,
    ) : Søknad() {

        override fun lukk(
            lukketAv: Saksbehandler,
            type: LukketType
        ): Lukket {
            return Lukket(
                sakId = sakId,
                id = id,
                opprettet = opprettet,
                søknadInnhold = søknadInnhold,
                journalpostId = null,
                oppgaveId = null,
                lukketAv = lukketAv,
                lukketType = type
            )
        }

        fun journalfør(
            journalpostId: JournalpostId,
        ): Journalført.UtenOppgave {
            return Journalført.UtenOppgave(
                sakId = sakId,
                id = id,
                opprettet = opprettet,
                søknadInnhold = søknadInnhold,
                journalpostId = journalpostId,
            )
        }
    }

    data class Lukket(
        override val sakId: UUID,
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt = now(),
        override val søknadInnhold: SøknadInnhold,
        // Det er mulig å lukke søknader uten at de er journalført og/eller laget oppgave.
        val journalpostId: JournalpostId?,
        val oppgaveId: OppgaveId?,
        val lukketTidspunkt: Tidspunkt = Tidspunkt.now(),
        val lukketAv: NavIdentBruker.Saksbehandler,
        val lukketType: LukketType,
        val lukketJournalpostId: JournalpostId? = null,
        val lukketBrevbestillingId: BrevbestillingId? = null
    ) : Søknad() {

        enum class LukketType(val value: String) {
            TRUKKET("TRUKKET"),
            BORTFALT("BORTFALT"),
            AVVIST("AVVIST");

            override fun toString() = value
        }

        /**
         * Returnerer seg selv.
         */
        override fun lukk(
            lukketAv: Saksbehandler,
            type: LukketType
        ) = this

        fun medJournalpostId(journalpostId: JournalpostId): Lukket {
            return this.copy(lukketJournalpostId = journalpostId)
        }

        fun medBrevbestillingId(brevbestillingId: BrevbestillingId): Lukket {
            return this.copy(lukketBrevbestillingId = brevbestillingId)
        }
    }

    sealed class Journalført : Søknad() {
        abstract val journalpostId: JournalpostId

        data class UtenOppgave(
            override val sakId: UUID,
            override val id: UUID = UUID.randomUUID(),
            override val opprettet: Tidspunkt = now(),
            override val søknadInnhold: SøknadInnhold,
            override val journalpostId: JournalpostId,
        ) : Journalført() {

            override fun lukk(
                lukketAv: Saksbehandler,
                type: LukketType
            ): Lukket {
                return Lukket(
                    sakId = sakId,
                    id = id,
                    opprettet = opprettet,
                    søknadInnhold = søknadInnhold,
                    journalpostId = journalpostId,
                    oppgaveId = null,
                    lukketAv = lukketAv,
                    lukketType = type
                )
            }

            fun medOppgave(
                oppgaveId: OppgaveId,
            ): MedOppgave {
                return MedOppgave(
                    sakId = sakId,
                    id = id,
                    opprettet = opprettet,
                    søknadInnhold = søknadInnhold,
                    journalpostId = journalpostId,
                    oppgaveId = oppgaveId
                )
            }
        }

        data class MedOppgave(
            override val sakId: UUID,
            override val id: UUID = UUID.randomUUID(),
            override val opprettet: Tidspunkt = now(),
            override val søknadInnhold: SøknadInnhold,
            override val journalpostId: JournalpostId,
            val oppgaveId: OppgaveId,
        ) : Journalført() {
            override fun lukk(
                lukketAv: Saksbehandler,
                type: LukketType
            ): Lukket {
                return Lukket(
                    sakId = sakId,
                    id = id,
                    opprettet = opprettet,
                    søknadInnhold = søknadInnhold,
                    journalpostId = journalpostId,
                    oppgaveId = oppgaveId,
                    lukketAv = lukketAv,
                    lukketType = type
                )
            }
        }
    }
}
