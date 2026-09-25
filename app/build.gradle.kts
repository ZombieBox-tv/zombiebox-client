plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.diegog0477.zombiebox.client"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "io.github.diegog0477.zombiebox.client"
        minSdk = 9
        // Sideload-only spike. Not a Play Store release configuration.
        targetSdk = 35
        versionCode = 53
        versionName = "0.1.0-dev.53"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    bundle { language { enableSplit = false } }
    lint { abortOnError = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) } }

dependencies {
    implementation(project(":shared"))
    testImplementation("junit:junit:4.13.2")
}
