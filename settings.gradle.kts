pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // only for zxing-cpp, so no other dependency can be resolved from it
        maven {
            setUrl("https://jitpack.io")
            content { includeGroup("com.github.markusfisch") }
        }
    }
}
rootProject.name = "pdfwallet"

include(":app")
include(":barcode")
include(":coroutines")
include(":logger")
include(":pdfdecryptor")
include(":pdfimporter")
include(":pdfrenderer")
