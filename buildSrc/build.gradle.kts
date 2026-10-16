plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.plugin.com.android.library)
    implementation(libs.plugin.com.android.application)
    implementation(libs.plugin.com.google.dagger.hilt.android)
    implementation(libs.plugin.org.jetbrains.kotlin.compose)
    implementation(libs.plugin.org.jetbrains.kotlin.serialization)
    implementation(libs.plugin.com.google.devtools.ksp)
    implementation(libs.plugin.io.gitlab.arturbosch.detekt)
}
