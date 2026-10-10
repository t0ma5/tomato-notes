import java.io.FileInputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.properties.Properties

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
    base
}

base {
    archivesName.set("notes-${libs.versions.app.version.versionCode.get()}")
}

val localArm64 = providers.gradleProperty("localArm64").orNull.equals("true", ignoreCase = true)

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        applicationId = libs.versions.app.version.appId.get()
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        targetSdk = project.libs.versions.app.build.targetSDK.get().toInt()
        versionName = project.libs.versions.app.version.versionName.get()
        versionCode = project.libs.versions.app.version.versionCode.get().toInt()
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            register("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            if (localArm64) {
                include("arm64-v8a")
                isUniversalApk = false
            } else {
                include("arm64-v8a", "armeabi-v7a", "x86_64")
                isUniversalApk = true
            }
        }
    }

    flavorDimensions.add("variants")
    productFlavors {
        register("core")
        register("fdroid")
        register("prepaid")
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
    }

    compileOptions {
        val currentJavaVersionFromLibs = JavaVersion.valueOf(libs.versions.app.build.javaVersion.get().toString())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    namespace = libs.versions.app.version.appId.get()

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

configurations.configureEach {
    resolutionStrategy {
        force("com.github.naveensingh:rtl-viewpager:2.0.2")
    }
}

dependencies {
    implementation(libs.simple.tools.commons)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.markwon.core)

    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

tasks.configureEach {
    if (name != "assembleCoreRelease") {
        return@configureEach
    }
    doLast {
        val version = libs.versions.app.version.versionName.get()
        val dir = layout.buildDirectory.dir("outputs/apk/core/release").get().asFile
        val abis = if (localArm64) listOf("arm64-v8a") else listOf("arm64-v8a", "armeabi-v7a", "x86_64", "universal")
        abis.forEach { abi ->
            val dest = dir.resolve("Tomato-Notes_${version}-FOSS-${abi}.apk")
            val src = dir.listFiles()
                ?.filter { it.isFile && it.extension == "apk" && it.name.contains(abi) }
                ?.minByOrNull { if (it.name == dest.name) 0 else 1 }
                ?: error("No $abi APK in $dir")
            if (src.canonicalFile != dest.canonicalFile) {
                src.copyTo(dest, overwrite = true)
                src.delete()
            }
        }
    }
}
