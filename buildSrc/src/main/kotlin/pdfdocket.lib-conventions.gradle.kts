plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("io.gitlab.arturbosch.detekt")
}

android {
    compileSdk = Versions.COMPILE_SDK
    androidResources.enable = false
    buildFeatures {
        buildConfig = false
        resValues = false
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    defaultConfig {
        minSdk = Versions.MIN_SDK
    }
    lint {
        warningsAsErrors = true
    }
}

kotlin {
    explicitApi()
    jvmToolchain(Versions.JAVA)
    compilerOptions {
        allWarningsAsErrors = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.libDesugarJdkLibs)
    implementation(libs.libHiltAndroid)
    ksp(libs.libHiltCompiler)

    implementation(platform(libs.libComposeBom))
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}
