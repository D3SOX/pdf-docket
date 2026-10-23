import java.io.ByteArrayInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")

    id("io.gitlab.arturbosch.detekt")
}

android {
    compileSdk = Versions.COMPILE_SDK

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    defaultConfig {
        minSdk = Versions.MIN_SDK
        targetSdk = Versions.TARGET_SDK

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        jniLibs {
            // Stripping depends on the installed NDK, keeping the prebuilt libraries as they are makes the APK reproducible
            keepDebugSymbols += "**/*.so"
        }
    }

    lint {
        warningsAsErrors = true
        checkDependencies = true
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
        }
        release {
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // Without the keystore secret, e.g. on F-Droid's build server, the APK stays unsigned
            System.getenv("RELEASE_KEYSTORE_PROPERTIES")?.let { properties ->
                val keystoreProperties = Properties()
                keystoreProperties.load(ByteArrayInputStream(properties.toByteArray()))
                signingConfig = signingConfigs.create("release") {
                    keyAlias = keystoreProperties.getProperty("KEY_ALIAS")
                    keyPassword = keystoreProperties.getProperty("KEY_PASSWORD")
                    storeFile = rootProject.file("credentials/keystore.jks")
                    storePassword = keystoreProperties.getProperty("STORE_PASSWORD")
                }
            }
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    jvmToolchain(Versions.JAVA)
    compilerOptions {
        allWarningsAsErrors = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}


dependencies {
    coreLibraryDesugaring(libs.libDesugarJdkLibs)
    debugImplementation(libs.libLeakCanary)

    implementation(platform(libs.libKotlinBom))
    implementation(libs.libRoomKtx)
    implementation(libs.libHiltAndroid)

    ksp(libs.libRoomCompiler)
    ksp(libs.libHiltCompiler)

    implementation(platform(libs.libComposeBom))
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}
