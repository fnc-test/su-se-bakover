package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.database.avstemming.AvstemmingPostgresRepo
import no.nav.su.se.bakover.database.avstemming.AvstemmingRepo
import no.nav.su.se.bakover.database.behandling.BehandlingPostgresRepo
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.beregning.BeregningPostgresRepo
import no.nav.su.se.bakover.database.beregning.BeregningRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggPostgresRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.database.oppdrag.OppdragPostgresRepo
import no.nav.su.se.bakover.database.oppdrag.OppdragRepo
import no.nav.su.se.bakover.database.sak.SakPostgresRepo
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.database.søknad.SøknadPostgresRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import javax.sql.DataSource

object DatabaseBuilder {
    fun build(): DatabaseRepos {
        val databaseName = Config.databaseName
        val abstractDatasource = Postgres(
            jdbcUrl = Config.jdbcUrl,
            vaultMountPath = Config.vaultMountPath,
            databaseName = databaseName,
            username = "user",
            password = "pwd"
        ).build()

        Flyway(abstractDatasource.getDatasource(Postgres.Role.Admin), databaseName).migrate()

        val userDatastore = abstractDatasource.getDatasource(Postgres.Role.User)
        return DatabaseRepos(
            avstemmingRepo = AvstemmingPostgresRepo(userDatastore),
            utbetalingRepo = UtbetalingPostgresRepo(userDatastore),
            oppdragRepo = OppdragPostgresRepo(userDatastore),
            søknadRepo = SøknadPostgresRepo(userDatastore),
            behandlingRepo = BehandlingPostgresRepo(userDatastore),
            hendelsesloggRepo = HendelsesloggPostgresRepo(userDatastore),
            beregningRepo = BeregningPostgresRepo(userDatastore),
            sakRepo = SakPostgresRepo(userDatastore)
        )
    }

    fun build(embeddedDatasource: DataSource): DatabaseRepos {
        Flyway(embeddedDatasource, "postgres").migrate()
        return DatabaseRepos(
            avstemmingRepo = AvstemmingPostgresRepo(embeddedDatasource),
            utbetalingRepo = UtbetalingPostgresRepo(embeddedDatasource),
            oppdragRepo = OppdragPostgresRepo(embeddedDatasource),
            søknadRepo = SøknadPostgresRepo(embeddedDatasource),
            behandlingRepo = BehandlingPostgresRepo(embeddedDatasource),
            hendelsesloggRepo = HendelsesloggPostgresRepo(embeddedDatasource),
            beregningRepo = BeregningPostgresRepo(embeddedDatasource),
            sakRepo = SakPostgresRepo(embeddedDatasource)
        )
    }
}

data class DatabaseRepos(
    val avstemmingRepo: AvstemmingRepo,
    val utbetalingRepo: UtbetalingRepo,
    val oppdragRepo: OppdragRepo,
    val søknadRepo: SøknadRepo,
    val behandlingRepo: BehandlingRepo,
    val hendelsesloggRepo: HendelsesloggRepo,
    val beregningRepo: BeregningRepo,
    val sakRepo: SakRepo
)
