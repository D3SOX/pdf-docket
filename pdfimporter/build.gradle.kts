plugins {
    id("greenpass.lib-conventions")
}

android.namespace = "me.d3sox.pdfwallet.pdfimporter"

dependencies {
    implementation(project(":coroutines"))
    implementation(project(":pdfdecryptor"))
    implementation(project(":pdfrenderer"))
    implementation(project(":logger"))
}