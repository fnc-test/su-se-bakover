dependencies {
    implementation(project(":common:domain"))
    implementation(project(":hendelse:domain"))
    implementation(project(":dokument:domain"))
    testImplementation(project(":test-common"))
    // Bruker Beregning
    testImplementation(project(":domain"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("økonomi-domain")
}
