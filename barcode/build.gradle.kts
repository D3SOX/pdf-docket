plugins {
    id("greenpass.lib-conventions")
}

android.namespace = "me.d3sox.pdfwallet.barcode"

dependencies {
    implementation(project(":coroutines"))
    implementation(libs.com.github.markusfisch.zxing.cpp)
}
