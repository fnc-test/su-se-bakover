dependencies {
    testImplementation(project(":common"))
    testImplementation(project(":domain"))
    testImplementation(project(":test-common"))
    testImplementation(project(":service"))
    testImplementation(project(":web"))

    testImplementation("no.nav:kafka-embedded-env:3.1.6") {
        // Breaks build: exclude(group = "org.glassfish.jersey.ext", module = "jersey-bean-validation")
        // Breaks build: exclude(group = "org.glassfish", module = "jakarta.el")
        // Breaks build: exclude(group = "org.eclipse.jetty", module = "jetty-server")
        // Breaks build: exclude(group = "org.eclipse.jetty", module = "jetty-servlets")
        exclude(group = "org.eclipse.jetty", module = "jetty-webapp")
        exclude(group = "log4j") // module = "log4j"
        exclude(group = "io.netty") // module = "netty-handler"
        exclude(group = "io.grpc") // module = "grpc-core"
    }
}
