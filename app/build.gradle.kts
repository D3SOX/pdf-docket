plugins {
    id("greenpass.app-conventions")
}

android {
    namespace = "com.michaeltroger.gruenerpass"

    defaultConfig {
        applicationId = "me.d3sox.pdfwallet"
        // Bump both before tagging a release. The release workflow checks that the tag matches versionName,
        // and F-Droid reads both from here to detect updates.
        versionCode = 853
        versionName = "1.1.0"
    }
}

dependencies {
    implementation(project(":barcode"))
    implementation(project(":coroutines"))
    implementation(project(":logger"))
    implementation(project(":pdfimporter"))
    implementation(project(":pdfrenderer"))

    implementation(platform(libs.org.jetbrains.kotlinx.coroutines.bom))
    implementation(libs.org.jetbrains.kotlinx.coroutines.android)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.io.github.panpf.zoomimage.compose)
    implementation(libs.kotlinx.serialization.core)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.androidx.test.ext.junit.ktx)
    testImplementation(libs.io.kotest.assertions.core)
    testImplementation(libs.org.jetbrains.kotlinx.coroutines.test)
    testImplementation(libs.org.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.com.google.dagger.hilt.androidtesting)
    kspTest(libs.com.google.dagger.hilt.compiler)
}
