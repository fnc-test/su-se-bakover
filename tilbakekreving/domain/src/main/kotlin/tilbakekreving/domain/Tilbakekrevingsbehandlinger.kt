package tilbakekreving.domain

import java.util.UUID

/**
 * En samling av alle tilbakekrevingsbehandlinger for en sak.
 * @see [no.nav.su.se.bakover.utenlandsopphold.domain.RegistrerteUtenlandsopphold]
 */
data class Tilbakekrevingsbehandlinger(
    val sakId: UUID,
    val behandlinger: List<Tilbakekrevingsbehandling>,
) : List<Tilbakekrevingsbehandling> by behandlinger {
    init {
        this.map { it.versjon }.let {
            require(it.sorted() == it) {
                "Tilbakekrevingsbehandlinger for sak $sakId må være sortert etter versjon, men var: $it"
            }
            require(it.distinct() == it) {
                "Tilbakekrevingsbehandlinger for sak $sakId kan ikke inneholde duplikater: $it"
            }
        }
    }

    fun hent(id: TilbakekrevingsbehandlingId): Tilbakekrevingsbehandling? {
        val behandling = behandlinger.filter { it.id == id }

        return when {
            behandling.isEmpty() -> null
            behandling.size == 1 -> behandling[0]
            else -> throw IllegalStateException("Mer enn 1 tilbakekrevingsbehandling for unik id: $id")
        }
    }

    fun harÅpen() = this.behandlinger.any { it.erÅpen() }

    companion object {
        fun empty(sakId: UUID) = Tilbakekrevingsbehandlinger(sakId, listOf())
    }
}
