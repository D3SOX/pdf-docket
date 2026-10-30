plugins {
    id("greenpass.app-conventions")
}

android {
    namespace = "me.d3sox.pdfwallet"

    defaultConfig {
        applicationId = "me.d3sox.pdfwallet"
        // Bump both before tagging a release. The release workflow checks that the tag matches versionName,
        // and F-Droid reads both from here to detect updates.
        versionCode = 860
        versionName = "1.7.0"

        testInstrumentationRunner = "me.d3sox.pdfwallet.HiltTestRunner"
    }

    sourceSets {
        // for MigrationTestHelper. Robolectric tests only see the assets of the app, so debug builds include the schemas.
        getByName("debug").assets.directories += "$projectDir/schemas"
        getByName("androidTest").assets.directories += listOf("testdata_generic", "testdata_1d", "testdata_2d")
            .map { rootProject.file(it).path }
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
    implementation(libs.org.bouncycastle.bcprov.jdk15to18)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.ext.junit.ktx)
    testImplementation(libs.io.kotest.assertions.core)
    testImplementation(libs.org.jetbrains.kotlinx.coroutines.test)
    testImplementation(libs.org.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.com.google.dagger.hilt.androidtesting)
    kspTest(libs.com.google.dagger.hilt.compiler)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit.ktx)
    androidTestImplementation(libs.io.kotest.assertions.core)
    androidTestImplementation(libs.org.jetbrains.kotlinx.coroutines.test)
    androidTestImplementation(libs.com.google.dagger.hilt.androidtesting)
    // to draw barcodes onto test pages
    androidTestImplementation(libs.com.github.markusfisch.zxing.cpp)
    kspAndroidTest(libs.com.google.dagger.hilt.compiler)
}
