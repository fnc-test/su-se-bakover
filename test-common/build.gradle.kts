// Contains shared test-data, functions and extension funcions to be used across modules
dependencies {
    val kotestVersion = "4.6.2"

    implementation(project(":domain"))
    implementation(project(":common"))
    implementation(project(":client"))

    implementation("io.kotest:kotest-assertions-core:$kotestVersion")
    // TODO jah: Finn en måte å gjenbruke de versjonene her på.
    implementation("org.mockito.kotlin:mockito-kotlin:3.2.0")
}
