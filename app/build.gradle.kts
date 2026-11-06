plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.crewalert.missedalert"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.crewalert.missedalert"
        minSdk = 29          // Android 10+: Galaxy XCover Pro through A14-A17
        targetSdk = 36
        versionCode = 5
        versionName = "1.2.2-pilot"
    }

    // Release signing: values come from the build environment (GitHub secrets). The key is never in the repo.
    signingConfigs {
        create("release") {
            val ks = System.getenv("KEYSTORE_PATH")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Intentionally no third-party dependencies: framework APIs only.

// Exercise real Android SharedPreferences and org.json through Robolectric JVM tests.
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
