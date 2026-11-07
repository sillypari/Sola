plugins {
    id("com.android.application")
}

android {
    namespace = "dev.sola.hud"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "dev.sola.hud"
        minSdk = 26
        targetSdk = 29
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module"
            )
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(project(":core"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
}
