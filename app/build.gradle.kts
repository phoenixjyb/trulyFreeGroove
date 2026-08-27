import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) propertiesFile.inputStream().use(::load)
}
val configuredJamendoClientId =
    localProperties.getProperty("JAMENDO_CLIENT_ID") ?: System.getenv("JAMENDO_CLIENT_ID").orEmpty()

android {
    namespace = "com.trulyfreemusic.opengroove"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.trulyfreemusic.opengroove"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.6.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "JAMENDO_CLIENT_ID", "\"$configuredJamendoClientId\"")
            buildConfigField("boolean", "JAMENDO_CONFIGURED", configuredJamendoClientId.isNotBlank().toString())
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "JAMENDO_CLIENT_ID", "\"$configuredJamendoClientId\"")
            buildConfigField("boolean", "JAMENDO_CONFIGURED", configuredJamendoClientId.isNotBlank().toString())
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions.jvmTarget = "17"
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    lint {
        // Dependency versions are pinned to the current AGP 8.5 toolchain. In particular,
        // WorkManager 2.11 requires AGP 8.6+, which is outside this feature release.
        disable += "GradleDependency"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.work:work-runtime:2.10.5")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
