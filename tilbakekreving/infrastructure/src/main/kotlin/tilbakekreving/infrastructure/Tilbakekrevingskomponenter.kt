package tilbakekreving.infrastructure

import tilbakekreving.application.service.TilbakekrevingServices

/**
 * Et forsøk på modularisering av [no.nav.su.se.bakover.web.services.Services] og [no.nav.su.se.bakover.domain.DatabaseRepos] der de forskjellige modulene er ansvarlige for å wire opp sine komponenter.
 */
class Tilbakekrevingskomponenter(
    val repos: TilbakekrevingRepos,
    val services: TilbakekrevingServices,
)