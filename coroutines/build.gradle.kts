plugins {
    id("greenpass.lib-conventions")
}

android.namespace = "me.d3sox.pdfwallet.coroutines"

dependencies {
    implementation(platform(libs.org.jetbrains.kotlinx.coroutines.bom))
    implementation(libs.org.jetbrains.kotlinx.coroutines.android)
}
