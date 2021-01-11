package no.nav.su.se.bakover.common

import io.github.cdimascio.dotenv.dotenv
import no.nav.su.se.bakover.common.EnvironmentConfig.exists
import no.nav.su.se.bakover.common.EnvironmentConfig.getEnvironmentVariableOrDefault
import no.nav.su.se.bakover.common.EnvironmentConfig.getEnvironmentVariableOrThrow
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory

private object EnvironmentConfig {
    private val env by lazy {
        dotenv {
            ignoreIfMissing = true
            systemProperties = true
        }
    }

    fun getEnvironmentVariableOrThrow(environmentVariableName: String): String {
        return env[environmentVariableName] ?: throwMissingEnvironmentVariable(environmentVariableName)
    }

    fun getEnvironmentVariableOrDefault(environmentVariableName: String, default: String): String {
        return env[environmentVariableName] ?: default
    }

    fun exists(environmentVariableName: String): Boolean {
        return env[environmentVariableName] != null
    }

    private fun throwMissingEnvironmentVariable(environmentVariableName: String): Nothing {
        throw IllegalStateException("Mangler environment variabelen '$environmentVariableName'. Dersom du prøver kjøre lokalt må den legges til i '.env'-fila. Se eksempler i '.env.template'.")
    }
}

data class ApplicationConfig(
    val isLocalOrRunningTests: Boolean,
    val leaderPodLookupPath: String,
    val pdfgenLocal: Boolean,
    val corsAllowOrigin: String,
    val serviceUser: ServiceUserConfig,
    val azure: AzureConfig,
    val oppdrag: OppdragConfig,
    val database: DatabaseConfig,
    val clientsConfig: ClientsConfig,
    val frontendCallbackUrls: FrontendCallbackUrls,
    val kafkaConfig: KafkaConfig,
) {

    data class ServiceUserConfig(
        val username: String,
        val password: String,
    ) {
        override fun toString(): String {
            return "ServiceUser(username='$username', password='****')"
        }

        companion object {
            fun createFromEnvironmentVariables() = ServiceUserConfig(
                username = getEnvironmentVariableOrThrow("username"),
                password = getEnvironmentVariableOrThrow("password"),
            )

            fun createLocalConfig() = ServiceUserConfig(
                username = "unused",
                password = "unused",
            )
        }
    }

    data class AzureConfig(
        val clientSecret: String,
        val wellKnownUrl: String,
        val clientId: String,
        val backendCallbackUrl: String,
        val groups: AzureGroups,
    ) {
        data class AzureGroups(
            val attestant: String,
            val saksbehandler: String,
            val veileder: String,
            val drift: String,
        ) {
            fun asList() = listOf(attestant, saksbehandler, veileder, drift)
        }

        companion object {
            fun createFromEnvironmentVariables() = AzureConfig(
                clientSecret = getEnvironmentVariableOrThrow("AZURE_APP_CLIENT_SECRET"),
                wellKnownUrl = getEnvironmentVariableOrThrow("AZURE_APP_WELL_KNOWN_URL"),
                clientId = getEnvironmentVariableOrThrow("AZURE_APP_CLIENT_ID"),
                backendCallbackUrl = getEnvironmentVariableOrThrow("BACKEND_CALLBACK_URL"),
                groups = AzureGroups(
                    attestant = getEnvironmentVariableOrThrow("AZURE_GROUP_ATTESTANT"),
                    saksbehandler = getEnvironmentVariableOrThrow("AZURE_GROUP_SAKSBEHANDLER"),
                    veileder = getEnvironmentVariableOrThrow("AZURE_GROUP_VEILEDER"),
                    drift = getEnvironmentVariableOrThrow("AZURE_GROUP_DRIFT"),
                )
            )

            fun createLocalConfig() = AzureConfig(
                clientSecret = getEnvironmentVariableOrThrow("AZURE_APP_CLIENT_SECRET"),
                wellKnownUrl = getEnvironmentVariableOrDefault(
                    "AZURE_APP_WELL_KNOWN_URL",
                    "https://login.microsoftonline.com/966ac572-f5b7-4bbe-aa88-c76419c0f851/v2.0/.well-known/openid-configuration"
                ),
                clientId = getEnvironmentVariableOrDefault(
                    "AZURE_APP_CLIENT_ID",
                    "26a62d18-70ce-48a6-9f4d-664607bd5188"
                ),
                backendCallbackUrl = getEnvironmentVariableOrDefault(
                    "BACKEND_CALLBACK_URL",
                    "http://localhost:8080/callback"
                ),
                groups = AzureGroups(
                    attestant = getEnvironmentVariableOrDefault(
                        "AZURE_GROUP_ATTESTANT",
                        "d75164fa-39e6-4149-956e-8404bc9080b6"
                    ),
                    saksbehandler = getEnvironmentVariableOrDefault(
                        "AZURE_GROUP_SAKSBEHANDLER",
                        "0ba009c4-d148-4a51-b501-4b1cf906889d"
                    ),
                    veileder = getEnvironmentVariableOrDefault(
                        "AZURE_GROUP_VEILEDER",
                        "062d4814-8538-4f3a-bcb9-32821af7909a"
                    ),
                    drift = getEnvironmentVariableOrDefault(
                        "AZURE_GROUP_DRIFT",
                        "5ccd88bd-58d6-41a7-9652-5e0597b00f9b"
                    )
                )
            )
        }
    }

    data class OppdragConfig(
        val mqQueueManager: String,
        val mqPort: Int,
        val mqHostname: String,
        val mqChannel: String,
        val utbetaling: UtbetalingConfig,
        val avstemming: AvstemmingConfig,
        val simulering: SimuleringConfig,
    ) {
        data class UtbetalingConfig constructor(
            val mqSendQueue: String,
            val mqReplyTo: String,
        ) {
            companion object {
                fun createFromEnvironmentVariables() = UtbetalingConfig(
                    mqSendQueue = getEnvironmentVariableOrThrow("MQ_SEND_QUEUE_UTBETALING"),
                    mqReplyTo = getEnvironmentVariableOrThrow("MQ_REPLY_TO"),
                )
            }
        }

        data class AvstemmingConfig constructor(
            val mqSendQueue: String,
        ) {
            companion object {
                fun createFromEnvironmentVariables() = AvstemmingConfig(
                    mqSendQueue = getEnvironmentVariableOrThrow("MQ_SEND_QUEUE_AVSTEMMING"),
                )
            }
        }

        data class SimuleringConfig constructor(
            val url: String,
            val stsSoapUrl: String,
        ) {
            companion object {
                fun createFromEnvironmentVariables() = SimuleringConfig(
                    url = getEnvironmentVariableOrThrow("SIMULERING_URL"),
                    stsSoapUrl = getEnvironmentVariableOrThrow("STS_URL_SOAP"),
                )
            }
        }

        companion object {
            fun createFromEnvironmentVariables() = OppdragConfig(
                mqQueueManager = getEnvironmentVariableOrThrow("MQ_QUEUE_MANAGER"),
                mqPort = getEnvironmentVariableOrThrow("MQ_PORT").toInt(),
                mqHostname = getEnvironmentVariableOrThrow("MQ_HOSTNAME"),
                mqChannel = getEnvironmentVariableOrThrow("MQ_CHANNEL"),
                utbetaling = UtbetalingConfig.createFromEnvironmentVariables(),
                avstemming = AvstemmingConfig.createFromEnvironmentVariables(),
                simulering = SimuleringConfig.createFromEnvironmentVariables(),
            )

            fun createLocalConfig() = OppdragConfig(
                mqQueueManager = "unused",
                mqPort = -1,
                mqHostname = "unused",
                mqChannel = "unused",
                utbetaling = UtbetalingConfig(
                    mqSendQueue = "unused",
                    mqReplyTo = "unused"
                ),
                avstemming = AvstemmingConfig(mqSendQueue = "unused"),
                simulering = SimuleringConfig(
                    url = "unused",
                    stsSoapUrl = "unused"
                )
            )
        }
    }

    sealed class DatabaseConfig {
        abstract val jdbcUrl: String

        data class RotatingCredentials(
            val databaseName: String,
            override val jdbcUrl: String,
            val vaultMountPath: String,
        ) : DatabaseConfig()

        data class StaticCredentials(
            override val jdbcUrl: String,
        ) : DatabaseConfig() {
            val username = "user"
            val password = "pwd"
        }

        companion object {
            fun createFromEnvironmentVariables() = RotatingCredentials(
                databaseName = getEnvironmentVariableOrThrow("DATABASE_NAME"),
                jdbcUrl = getEnvironmentVariableOrThrow("DATABASE_JDBC_URL"),
                vaultMountPath = getEnvironmentVariableOrThrow("VAULT_MOUNTPATH"),
            )

            fun createLocalConfig() = StaticCredentials(
                jdbcUrl = getEnvironmentVariableOrDefault(
                    "DATABASE_JDBC_URL",
                    "jdbc:postgresql://localhost:5432/supstonad-db-local"
                ),
            )
        }
    }

    data class ClientsConfig(
        val oppgaveConfig: OppgaveConfig,
        val pdlUrl: String,
        val dokDistUrl: String,
        val pdfgenUrl: String,
        val dokarkivUrl: String,
        val kodeverkUrl: String,
        val stsUrl: String,
        val skjermingUrl: String,
        val dkifUrl: String,
    ) {
        companion object {
            fun createFromEnvironmentVariables() = ClientsConfig(
                oppgaveConfig = OppgaveConfig.createFromEnvironmentVariables(),
                pdlUrl = getEnvironmentVariableOrDefault("PDL_URL", "http://pdl-api.default.svc.nais.local"),
                dokDistUrl = getEnvironmentVariableOrThrow("DOKDIST_URL"),
                pdfgenUrl = getEnvironmentVariableOrDefault("PDFGEN_URL", "http://su-pdfgen.supstonad.svc.nais.local"),
                dokarkivUrl = getEnvironmentVariableOrThrow("DOKARKIV_URL"),
                kodeverkUrl = getEnvironmentVariableOrDefault("KODEVERK_URL", "http://kodeverk.default.svc.nais.local"),
                stsUrl = getEnvironmentVariableOrDefault(
                    "STS_URL",
                    "http://security-token-service.default.svc.nais.local"
                ),
                skjermingUrl = getEnvironmentVariableOrThrow("SKJERMING_URL"),
                dkifUrl = getEnvironmentVariableOrDefault("DKIF_URL", "http://dkif.default.svc.nais.local"),
            )

            fun createLocalConfig() = ClientsConfig(
                oppgaveConfig = OppgaveConfig.createLocalConfig(),
                pdlUrl = "mocked",
                dokDistUrl = "mocked",
                pdfgenUrl = "mocked",
                dokarkivUrl = "mocked",
                kodeverkUrl = "mocked",
                stsUrl = "mocked",
                skjermingUrl = "mocked",
                dkifUrl = "mocked",
            )
        }

        data class OppgaveConfig(
            val clientId: String,
            val url: String,
        ) {
            companion object {
                fun createFromEnvironmentVariables() = OppgaveConfig(
                    clientId = getEnvironmentVariableOrThrow("OPPGAVE_CLIENT_ID"),
                    url = getEnvironmentVariableOrThrow("OPPGAVE_URL"),
                )

                fun createLocalConfig() = OppgaveConfig(
                    clientId = "mocked",
                    url = "mocked",
                )
            }
        }
    }

    data class FrontendCallbackUrls(
        private val frontendBaseUrl: String,
    ) {
        val suSeFramoverLoginSuccessUrl = "$frontendBaseUrl/auth/complete"
        val suSeFramoverLogoutSuccessUrl = "$frontendBaseUrl/logout/complete"

        companion object {
            fun createFromEnvironmentVariables() = FrontendCallbackUrls(
                frontendBaseUrl = getEnvironmentVariableOrThrow("FRONTEND_BASE_URL")
            )

            fun createLocalConfig() = FrontendCallbackUrls(
                frontendBaseUrl = "http://localhost:1234"
            )
        }
    }

    data class KafkaConfig(
        private val common: Map<String, String>,
        val producerConfig: Map<String, Any>,
    ) {
        companion object {
            fun createFromEnvironmentVariables() = KafkaConfig(
                common = Common().configure(),
                producerConfig = Common().configure() + mapOf(
                    ProducerConfig.ACKS_CONFIG to "all",
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                    "RETRY_INTERVAL" to 15_000L
                )
            )

            fun createLocalConfig() = KafkaConfig(
                common = emptyMap(),
                producerConfig = emptyMap()
            )
        }

        private data class Common(
            val brokers: String = getEnvironmentVariableOrDefault("KAFKA_BROKERS", "brokers"),
            val sslConfig: Map<String, String> = SslConfig().configure()
        ) {
            fun configure(): Map<String, String> =
                mapOf(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to brokers) + sslConfig
        }

        private data class SslConfig(
            val truststorePath: String = getEnvironmentVariableOrDefault("KAFKA_TRUSTSTORE_PATH", "truststorePath"),
            val keystorePath: String = getEnvironmentVariableOrDefault("KAFKA_KEYSTORE_PATH", "keystorePath"),
            val credstorePwd: String = getEnvironmentVariableOrDefault("KAFKA_CREDSTORE_PASSWORD", "credstorePwd"),
        ) {
            fun configure(): Map<String, String> = mapOf(
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to SecurityProtocol.SSL.name,
                SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "", // Disable server host name verification
                SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to "jks",
                SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to "PKCS12",
                SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to truststorePath,
                SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to credstorePwd,
                SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to keystorePath,
                SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to credstorePwd,
                SslConfigs.SSL_KEY_PASSWORD_CONFIG to credstorePwd
            )
        }
    }

    companion object {

        private val log by lazy {
            // We have to delay logback initialization until after we can determine if we are running locally or not.
            // Ref logback-local.xml
            LoggerFactory.getLogger(this::class.java)
        }

        fun createConfig() = if (isLocalOrRunningTests()) createLocalConfig() else createFromEnvironmentVariables()

        fun createFromEnvironmentVariables() = ApplicationConfig(
            isLocalOrRunningTests = false,
            leaderPodLookupPath = getEnvironmentVariableOrThrow("ELECTOR_PATH"),
            pdfgenLocal = false,
            corsAllowOrigin = getEnvironmentVariableOrThrow("ALLOW_CORS_ORIGIN"),
            serviceUser = ServiceUserConfig.createFromEnvironmentVariables(),
            azure = AzureConfig.createFromEnvironmentVariables(),
            oppdrag = OppdragConfig.createFromEnvironmentVariables(),
            database = DatabaseConfig.createFromEnvironmentVariables(),
            clientsConfig = ClientsConfig.createFromEnvironmentVariables(),
            frontendCallbackUrls = FrontendCallbackUrls.createFromEnvironmentVariables(),
            kafkaConfig = KafkaConfig.createFromEnvironmentVariables()
        )

        fun createLocalConfig() = ApplicationConfig(
            isLocalOrRunningTests = true,
            leaderPodLookupPath = "",
            pdfgenLocal = getEnvironmentVariableOrDefault("PDFGEN_LOCAL", "false").toBoolean(),
            corsAllowOrigin = "localhost:1234",
            serviceUser = ServiceUserConfig.createLocalConfig(),
            azure = AzureConfig.createLocalConfig(),
            oppdrag = OppdragConfig.createLocalConfig(),
            database = DatabaseConfig.createLocalConfig(),
            clientsConfig = ClientsConfig.createLocalConfig(),
            frontendCallbackUrls = FrontendCallbackUrls.createLocalConfig(),
            kafkaConfig = KafkaConfig.createLocalConfig()
        ).also {
            log.warn("**********  Using local config (the environment variable 'NAIS_CLUSTER_NAME' is missing.)")
        }

        fun isLocalOrRunningTests() = !exists("NAIS_CLUSTER_NAME")
    }
}
