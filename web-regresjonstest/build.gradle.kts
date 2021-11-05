// Inneholder regresjonstester for web-laget (black-box asserting).
// Separert til sin egen modul for å kunne bygges parallelt med de andre testene på byggserveren.
val ktorVersion: String by project
dependencies {
    testImplementation(project(":domain"))
    testImplementation(project(":database"))
    testImplementation(project(":web"))
    testImplementation(project(":client"))
    testImplementation(project(":common"))
    testImplementation(project(":service"))
    testImplementation(project(":test-common"))
    testImplementation(project(":database", "testArchives"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "junit")
        exclude(group = "org.eclipse.jetty") // conflicts with WireMock
        exclude(group = "org.eclipse.jetty.http2") // conflicts with WireMock
    }
}