plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// --- Versioning -------------------------------------------------------------------
// versionName is set by hand (see CHANGELOG.md). versionCode and the commit id come
// from git, so every commit builds a higher number and each build says what code it
// is. Without git (e.g. a source zip) they fall back to 1 / "unknown".
val appVersionName = "0.2.0"

fun git(vararg args: String): String? = try {
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().takeIf { it.isNotEmpty() }
} catch (e: Exception) {
    null
}

val gitCommitCount = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
val gitCommit = (git("rev-parse", "--short", "HEAD") ?: "unknown") +
    if (git("status", "--porcelain") != null) "-dirty" else ""

android {
    namespace = "com.yoreader.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yoreader.app"
        minSdk = 26
        targetSdk = 35
        versionCode = gitCommitCount
        versionName = appVersionName
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    // "Clean up": main-content extraction from web pages.
    implementation(libs.readability4j)
    implementation(libs.jsoup)

    testImplementation(libs.junit)
    // Real XmlPullParser for JVM unit tests (android.jar only has stubs).
    testImplementation(libs.kxml2)
}
