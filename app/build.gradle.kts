plugins {
    id("com.android.application"); id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose"); id("com.google.devtools.ksp")
}
val webClientId: String = (project.findProperty("GOOGLE_WEB_CLIENT_ID") as String?) ?: System.getenv("GOOGLE_WEB_CLIENT_ID") ?: ""
val keystoreFile: File? = System.getenv("KEYSTORE_PATH")?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }

android {
    namespace = "com.mono.player"; compileSdk = 35
    defaultConfig { applicationId = "com.mono.player"; minSdk = 26; targetSdk = 35; versionCode = 4; versionName = "1.3"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$webClientId\"") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    signingConfigs {
        if (keystoreFile != null) create("nyxen") {
            storeFile = keystoreFile
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreFile != null) signingConfigs.getByName("nyxen") else signingConfigs.getByName("debug")
        }
    }
}

val generateOfflinePack by tasks.registering(Exec::class) {
    commandLine("python3", rootProject.file("tools/generate_offline_pack.py"))
}
tasks.named("preBuild").configure { dependsOn(generateOfflinePack) }

dependencies {
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
