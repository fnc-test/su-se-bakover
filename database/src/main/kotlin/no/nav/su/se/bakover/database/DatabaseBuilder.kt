package no.nav.su.se.bakover.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.ApplicationConfig.DatabaseConfig.RotatingCredentials
import no.nav.su.se.bakover.common.ApplicationConfig.DatabaseConfig.StaticCredentials
import no.nav.su.se.bakover.database.avkorting.AvkortingsvarselPostgresRepo
import no.nav.su.se.bakover.database.avstemming.AvstemmingPostgresRepo
import no.nav.su.se.bakover.database.dokument.DokumentPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.BosituasjongrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FormueVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FormuegrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FradragsgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.GrunnlagsdataOgVilkårsvurderingerPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UføreVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UføregrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UtenlandsoppholdVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UtenlandsoppholdgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.hendelse.PersonhendelsePostgresRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggPostgresRepo
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo
import no.nav.su.se.bakover.database.klage.klageinstans.KlageinstanshendelsePostgresRepo
import no.nav.su.se.bakover.database.kontrollsamtale.KontrollsamtalePostgresRepo
import no.nav.su.se.bakover.database.nøkkeltall.NøkkeltallPostgresRepo
import no.nav.su.se.bakover.database.person.PersonPostgresRepo
import no.nav.su.se.bakover.database.regulering.ReguleringPostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo
import no.nav.su.se.bakover.database.sak.SakPostgresRepo
import no.nav.su.se.bakover.database.søknad.SøknadPostgresRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepo
import no.nav.su.se.bakover.database.vedtak.VedtakPostgresRepo
import no.nav.su.se.bakover.domain.DatabaseRepos
import org.jetbrains.annotations.TestOnly
import java.time.Clock
import javax.sql.DataSource

object DatabaseBuilder {
    fun build(
        databaseConfig: ApplicationConfig.DatabaseConfig,
        dbMetrics: DbMetrics,
        clock: Clock,
    ): DatabaseRepos {
        val abstractDatasource = Postgres(databaseConfig = databaseConfig).build()

        val dataSource = abstractDatasource.getDatasource(Postgres.Role.Admin)
        when (databaseConfig) {
            is StaticCredentials -> {
                // Lokalt ønsker vi ikke noe herjing med rolle; Docker-oppsettet sørger for at vi har riktige tilganger her.
                Flyway(dataSource)
            }
            is RotatingCredentials -> Flyway(
                dataSource = dataSource,
                // Pga roterende credentials i preprod/prod må tabeller opprettes/endres av samme rolle hver gang. Se https://github.com/navikt/utvikling/blob/master/PostgreSQL.md#hvordan-kj%C3%B8re-flyway-migreringerendre-p%C3%A5-databaseskjemaet
                role = "${databaseConfig.databaseName}-${Postgres.Role.Admin}",
            )
        }.migrate()

        val userDatastore = abstractDatasource.getDatasource(Postgres.Role.User)
        return buildInternal(userDatastore, dbMetrics, clock)
    }

    @TestOnly
    fun build(
        embeddedDatasource: DataSource,
        dbMetrics: DbMetrics,
        clock: Clock,
    ): DatabaseRepos {
        // I testene ønsker vi ikke noe herjing med rolle; embedded-oppsettet sørger for at vi har riktige tilganger og er ferdigmigrert her.
        return buildInternal(embeddedDatasource, dbMetrics, clock)
    }

    @TestOnly
    fun newLocalDataSource(): DataSource {
        val dbConfig = ApplicationConfig.DatabaseConfig.createLocalConfig()
        return HikariDataSource(
            HikariConfig().apply {
                this.jdbcUrl = dbConfig.jdbcUrl
                this.maximumPoolSize = 3
                this.minimumIdle = 1
                this.idleTimeout = 10001
                this.connectionTimeout = 1000
                this.maxLifetime = 30001
                this.username = dbConfig.username
                this.password = dbConfig.password
            },
        )
    }

    @TestOnly
    fun migrateDatabase(dataSource: DataSource) {
        Flyway(dataSource).migrate()
    }

    internal fun buildInternal(
        dataSource: DataSource,
        dbMetrics: DbMetrics,
        clock: Clock,
    ): DatabaseRepos {
        val sessionFactory = PostgresSessionFactory(dataSource)

        val avkortingsvarselRepo = AvkortingsvarselPostgresRepo(sessionFactory)

        val grunnlagsdataOgVilkårsvurderingerPostgresRepo = GrunnlagsdataOgVilkårsvurderingerPostgresRepo(
            bosituasjongrunnlagPostgresRepo = BosituasjongrunnlagPostgresRepo(
                dbMetrics = dbMetrics,
            ),
            fradragsgrunnlagPostgresRepo = FradragsgrunnlagPostgresRepo(
                dbMetrics = dbMetrics,
            ),
            uføreVilkårsvurderingPostgresRepo = UføreVilkårsvurderingPostgresRepo(
                uføregrunnlagRepo = UføregrunnlagPostgresRepo(),
                dbMetrics = dbMetrics,
            ),
            formueVilkårsvurderingPostgresRepo = FormueVilkårsvurderingPostgresRepo(
                formuegrunnlagPostgresRepo = FormuegrunnlagPostgresRepo(),
                dbMetrics = dbMetrics,
            ),
            utenlandsoppholdVilkårsvurderingPostgresRepo = UtenlandsoppholdVilkårsvurderingPostgresRepo(
                utenlandsoppholdgrunnlagRepo = UtenlandsoppholdgrunnlagPostgresRepo(),
                dbMetrics = dbMetrics,
            ),
        )

        val søknadsbehandlingRepo = SøknadsbehandlingPostgresRepo(
            dataSource = dataSource,
            grunnlagsdataOgVilkårsvurderingerPostgresRepo = grunnlagsdataOgVilkårsvurderingerPostgresRepo,
            dbMetrics = dbMetrics,
            sessionFactory = sessionFactory,
            avkortingsvarselRepo = avkortingsvarselRepo,
            clock = clock,
        )

        val klageinstanshendelseRepo = KlageinstanshendelsePostgresRepo(sessionFactory)
        val klageRepo = KlagePostgresRepo(sessionFactory, klageinstanshendelseRepo)
        val reguleringRepo = ReguleringPostgresRepo(
            dataSource = dataSource,
            grunnlagsdataOgVilkårsvurderingerPostgresRepo = grunnlagsdataOgVilkårsvurderingerPostgresRepo,
            sessionFactory = sessionFactory,
        )
        val revurderingRepo = RevurderingPostgresRepo(
            dataSource = dataSource,
            grunnlagsdataOgVilkårsvurderingerPostgresRepo = grunnlagsdataOgVilkårsvurderingerPostgresRepo,
            søknadsbehandlingRepo = søknadsbehandlingRepo,
            klageRepo = klageRepo,
            dbMetrics = dbMetrics,
            sessionFactory = sessionFactory,
            avkortingsvarselRepo = avkortingsvarselRepo,
            reguleringPostgresRepo = reguleringRepo,
        )
        val vedtakRepo = VedtakPostgresRepo(
            dataSource = dataSource,
            søknadsbehandlingRepo = søknadsbehandlingRepo,
            revurderingRepo = revurderingRepo,
            klageRepo = klageRepo,
            dbMetrics = dbMetrics,
            sessionFactory = sessionFactory,
            reguleringRepo = reguleringRepo,
        )
        val hendelseRepo = PersonhendelsePostgresRepo(dataSource, clock)
        val nøkkeltallRepo = NøkkeltallPostgresRepo(dataSource, clock)
        val kontrollsamtaleRepo = KontrollsamtalePostgresRepo(sessionFactory)

        return DatabaseRepos(
            avstemming = AvstemmingPostgresRepo(dataSource),
            utbetaling = UtbetalingPostgresRepo(
                dataSource = dataSource,
                dbMetrics = dbMetrics,
                sessionFactory = sessionFactory,
            ),
            søknad = SøknadPostgresRepo(
                dataSource = dataSource,
                dbMetrics = dbMetrics,
                postgresSessionFactory = sessionFactory,
            ),
            hendelseslogg = HendelsesloggPostgresRepo(dataSource),
            sak = SakPostgresRepo(
                sessionFactory = sessionFactory,
                søknadsbehandlingRepo = søknadsbehandlingRepo,
                revurderingRepo = revurderingRepo,
                vedtakPostgresRepo = vedtakRepo,
                dbMetrics = dbMetrics,
                klageRepo = klageRepo,
                reguleringRepo = reguleringRepo
            ),
            person = PersonPostgresRepo(
                dataSource = dataSource,
                dbMetrics = dbMetrics,
            ),
            søknadsbehandling = søknadsbehandlingRepo,
            revurderingRepo = revurderingRepo,
            vedtakRepo = vedtakRepo,
            personhendelseRepo = hendelseRepo,
            dokumentRepo = DokumentPostgresRepo(dataSource, sessionFactory),
            nøkkeltallRepo = nøkkeltallRepo,
            sessionFactory = sessionFactory,
            klageRepo = klageRepo,
            klageinstanshendelseRepo = klageinstanshendelseRepo,
            kontrollsamtaleRepo = kontrollsamtaleRepo,
            avkortingsvarselRepo = avkortingsvarselRepo,
            reguleringRepo = reguleringRepo,
        )
    }
}
