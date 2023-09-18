dependencies {
    implementation(project(":common:domain"))
    implementation(project(":hendelse:domain"))

    testImplementation(project(":test-common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("tilbakekreving-domain")
}

tasks.test {
    useJUnitPlatform()
}