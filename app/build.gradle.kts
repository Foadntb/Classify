plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// آدرس سرور داخل اپ ساخته می‌شود (کاربر هیچ‌وقت آن را تایپ نمی‌کند).
// در GitHub: Settings ← Secrets and variables ← Actions ← Variables ← CLASSIFY_SERVER_URL
val serverUrl: String = (System.getenv("CLASSIFY_SERVER_URL")
    ?: providers.gradleProperty("classifyServerUrl").orNull
    ?: "").trim().trimEnd('/')

// امضای ثابت: بدون آن، هر بیلد با کلید موقت امضا می‌شود و نسخهٔ جدید روی قبلی نصب نمی‌شود.
val keystoreFile: String? = System.getenv("CLASSIFY_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

android {
    namespace = "ir.fntb.classify"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        // شمارهٔ هر بیلد GitHub به‌عنوان versionCode؛ تا نسخه‌های جدید روی قبلی نصب شوند
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.3"

        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
        // اگر آدرس https باشد، ترافیک بدون رمزنگاری کلاً ممنوع می‌شود
        manifestPlaceholders["usesCleartext"] = if (serverUrl.startsWith("https://")) "false" else "true"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("classify") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("CLASSIFY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CLASSIFY_KEY_ALIAS")
                keyPassword = System.getenv("CLASSIFY_KEY_PASSWORD")
            }
        }
    }

    flavorDimensions += "role"
    productFlavors {
        // اپ دانش‌آموز
        create("student") {
            dimension = "role"
            applicationId = "ir.fntb.classify"
            resValue("string", "app_name", "Classify")
            buildConfigField("boolean", "MANAGER_APP", "false")
        }
        // اپ مدیریت (مدیر کلاس و مدیر کل)
        create("manager") {
            dimension = "role"
            applicationId = "ir.fntb.classify.manager"
            resValue("string", "app_name", "Classify Manager")
            buildConfigField("boolean", "MANAGER_APP", "true")
        }
    }

    buildTypes {
        debug {
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("classify")
        }
        release {
            isMinifyEnabled = false
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("classify")
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

    sourceSets["main"].java.srcDir("src/main/kotlin")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
