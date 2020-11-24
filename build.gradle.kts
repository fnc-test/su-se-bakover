buildscript {
    dependencies {
        classpath("org.ajoberstar:grgit:2.3.0")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.4.20"
    id("org.jlleitschuh.gradle.ktlint") version "9.4.1"
    id("com.github.ben-manes.versions") version "0.31.0" // Finds latest versions
    id("se.patrikerdes.use-latest-versions") version "0.2.14"
}

version = "0.0.1"

allprojects {
    val githubUser: String by project
    val githubPassword: String by project
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "com.github.ben-manes.versions")
    apply(plugin = "se.patrikerdes.use-latest-versions")
    repositories {
        jcenter()
        maven("https://dl.bintray.com/kotlin/ktor")
        maven("http://packages.confluent.io/maven/")
        maven {
            url = uri("https://maven.pkg.github.com/navikt/tjenestespesifikasjoner")
            credentials {
                username = githubUser
                password = githubPassword
            }
        }
    }
    val junitJupiterVersion = "5.7.0"
    val arrowVersion = "0.11.0"
    val kotestVersion = "4.3.1"
    val jacksonVersion = "2.11.3"
    val ktlintVersion = "0.39.0"
    dependencies {
        api(kotlin("stdlib-jdk8"))

        implementation("org.jetbrains.kotlin:kotlin-reflect:1.4.20")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.1")
        implementation("io.arrow-kt:arrow-core:$arrowVersion")
        implementation("io.arrow-kt:arrow-syntax:$arrowVersion")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
        implementation("ch.qos.logback:logback-classic:1.2.3")
        implementation("net.logstash.logback:logstash-logback-encoder:6.4")
        implementation("io.github.cdimascio:java-dotenv:5.2.2")

        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")

        testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
        testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
        testImplementation("io.kotest:kotest-assertions-json-jvm:$kotestVersion")
        testImplementation("io.kotest:kotest-assertions-arrow-jvm:$kotestVersion")
        testImplementation("org.skyscreamer:jsonassert:1.5.0")
        testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
        testImplementation("org.mockito:mockito-core:3.6.0")

        constraints {
            implementation("io.netty:netty-codec-http2:4.1.53.Final") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-IONETTY-1020439")
            }
            implementation("commons-collections:commons-collections:3.2.2") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-COMMONSCOLLECTIONS-30078")
            }
            implementation("com.google.guava:guava:30.0-jre") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-COMGOOGLEGUAVA-1015415")
            }
            implementation("org.apache.httpcomponents:httpclient:4.5.13") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-ORGAPACHEHTTPCOMPONENTS-1016906")
            }
            implementation("org.postgresql:postgresql:42.2.13") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-ORGPOSTGRESQL-571481")
            }
            implementation("org.apache.cxf:cxf-rt-transports-http:3.4.1") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-ORGAPACHECXF-1039798")
            }
            implementation("junit:junit:4.13.1") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-JUNIT-1017047")
            }
            implementation("org.eclipse.jetty:jetty-webapp:9.4.33.v20201020") {
                because("https://app.snyk.io/vuln/SNYK-JAVA-ORGECLIPSEJETTY-1021614")
            }
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "12"
            freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
            allWarningsAsErrors = true
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    tasks.withType<Wrapper> {
        gradleVersion = "6.7"
    }

    tasks.named("dependencyUpdates", com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask::class.java)
        .configure {
            checkForGradleUpdate = true
            gradleReleaseChannel = "current"
            outputFormatter = "json"
            outputDir = "build/dependencyUpdates"
            reportfileName = "report"
            revision = "release" // Not waterproof
        }

    ktlint {
        this.version.set(ktlintVersion)
    }
}
