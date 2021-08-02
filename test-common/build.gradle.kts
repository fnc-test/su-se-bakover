// Contains shared test-data, functions and extension funcions to be used across modules
dependencies {
    val kotestVersion = "4.6.1"

    implementation(project(":domain"))
    implementation(project(":common"))
    implementation(project(":client"))

    implementation("io.kotest:kotest-assertions-core:$kotestVersion")

}