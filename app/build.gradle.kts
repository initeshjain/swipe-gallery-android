import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localReleaseSigningFile = rootProject.file("tools/release-signing.properties")
val localReleaseSigning = Properties().apply {
    if (localReleaseSigningFile.isFile) {
        localReleaseSigningFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.swipegallery.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.swipegallery.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        if (localReleaseSigningFile.isFile) {
            create("localRelease") {
                storeFile = rootProject.file(localReleaseSigning.getProperty("storeFile"))
                storePassword = localReleaseSigning.getProperty("storePassword")
                keyAlias = localReleaseSigning.getProperty("keyAlias")
                keyPassword = localReleaseSigning.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (localReleaseSigningFile.isFile) {
                signingConfig = signingConfigs.getByName("localRelease")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
