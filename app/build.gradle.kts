plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.hilt)
}

// Release signing is configured entirely from the environment (populated by CI
// from GitHub Actions secrets). When the variables are absent, the release
// build type stays unsigned so local debug/release builds keep working.
val releaseKeystoreFile: String? = System.getenv("ANDROID_KEYSTORE_FILE")
val releaseKeystorePassword: String? = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("ANDROID_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("ANDROID_KEY_PASSWORD")
val hasReleaseSigning = !releaseKeystoreFile.isNullOrBlank() &&
        !releaseKeystorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

// App typeface selection. The repo ships Inter (OFL) so public/CI builds are
// license-clean. Söhne is commercially licensed and must never be committed:
// to use it locally, place its files under app/fonts/sohne/res (gitignored)
// and set FONT_FAMILY=Sohne in the environment or the root .env file. Both
// directories expose the same @font/app_font resource, so only one may be
// active at a time.
val fontFamilyFromEnvFile: String? = rootProject.file(".env")
    .takeIf { it.isFile }
    ?.readLines()
    ?.map { it.trim().removePrefix("export ").trim() }
    ?.lastOrNull { it.startsWith("FONT_FAMILY=") }
    ?.substringAfter("=")
    ?.trim()
    ?.trim('"', '\'')
val fontFamily = System.getenv("FONT_FAMILY")?.takeIf { it.isNotBlank() } ?: fontFamilyFromEnvFile
val sohneFontDir = file("fonts/sohne/res")
val useSohneFont = fontFamily.equals("Sohne", ignoreCase = true) && sohneFontDir.isDirectory
if (fontFamily.equals("Sohne", ignoreCase = true) && !useSohneFont) {
    logger.lifecycle("FONT_FAMILY=Sohne requested but app/fonts/sohne/res is missing; falling back to Inter.")
}

android {
    namespace = "com.dokeraj.androtainer"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.thayyil.androtainer"
        minSdk = 29
        targetSdk = 36
        versionCode = 22
        versionName = "2.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystoreFile!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    sourceSets {
        getByName("main") {
            res.srcDir(if (useSohneFont) "fonts/sohne/res" else "fonts/inter/res")
        }
    }
}

// AGP derives APK filenames (including Android Studio's Generate Signed APK
// export) from Gradle's archive base name. Keep the app name and version in
// that base so every producer emits AndroTainer_vX.Y.Z-<buildType>.apk.
base {
    archivesName.set("AndroTainer_v${android.defaultConfig.versionName}")
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val buildType = variant.buildType
        val versionName = variant.outputs.single().versionName
        val outputDirectory = rootProject.layout.projectDirectory.dir("builds")
        val copyTask = tasks.register<Copy>("copy${variantName}ApkToBuildsDir") {
            group = "distribution"
            description = "Copies the ${variant.name} APK to builds with its versioned filename."
            inputs.property("apkVersionName", versionName)
            doFirst {
                fileTree(outputDirectory) {
                    include("AndroTainer_v*-${buildType}.apk")
                }.files.forEach { it.delete() }
            }
            from(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK)) {
                include("*.apk")
                rename { "AndroTainer_v${versionName.get()}-${buildType}.apk" }
            }
            into(outputDirectory)
        }

        tasks.matching { it.name == "assemble${variantName}" }.configureEach {
            finalizedBy(copyTask)
        }
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.markwon.core)
    implementation(libs.markwon.linkify)
    implementation(libs.coil)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
