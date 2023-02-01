/*
 * This file was generated by the Gradle 'init' task.
 *
 * The settings file is used to specify which projects to include in your build.
 *
 * Detailed information about configuring a multi-project build in Gradle can be found
 * in the user manual at https://docs.gradle.org/5.6.3/userguide/multi_project_builds.html
 */

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        val kotlinVersion: String by settings
        kotlin("jvm") version kotlinVersion
    }
}

rootProject.name = "su-se-bakover"
include("domain")
include("web")
include("common")
include("service")
include("database")
include("client")
include("test-common")
include("web-regresjonstest")
include("statistikk")

include("hendelse:infrastructure")
include("hendelse:domain")

include("utenlandsopphold:infrastructure")
include("utenlandsopphold:application")
include("utenlandsopphold:domain")

include("datapakker:søknad")

include("kontrollsamtale:infrastructure")
include("kontrollsamtale:application")
include("kontrollsamtale:domain")
