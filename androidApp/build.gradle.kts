plugins {
  id("com.android.application")
  kotlin("android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.compose")
}

import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val keystoreProperties = Properties()
val keystorePropertiesFile = file("keystore.properties")
val hasReleaseKeystore = keystorePropertiesFile.exists().also { exists ->
  if (exists) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
  }
}

android {
  namespace = "dtv.mobile.android"
  compileSdk = 36
  buildToolsVersion = "36.1.0"

  defaultConfig {
    applicationId = "dtv.mobile"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.1"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildFeatures { compose = true }

  packaging {
    resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlin {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
    }
  }

  signingConfigs {
    create("release") {
      if (hasReleaseKeystore) {
        storeFile = file(keystoreProperties.getProperty("storeFile"))
        storePassword = keystoreProperties.getProperty("storePassword")
        keyAlias = keystoreProperties.getProperty("keyAlias")
        keyPassword = keystoreProperties.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      if (hasReleaseKeystore) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
  }
}

dependencies {
  implementation(project(":shared"))

  implementation("androidx.activity:activity-compose:1.9.2")
  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

  implementation("org.jetbrains.compose.material3:material3:1.9.0")
  implementation("org.jetbrains.compose.ui:ui:1.9.3")
  implementation("org.jetbrains.compose.foundation:foundation:1.9.3")
  implementation("org.jetbrains.compose.runtime:runtime:1.9.3")
  implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

  androidTestImplementation("androidx.test.ext:junit:1.2.1")
  androidTestImplementation("androidx.test:runner:1.6.2")
}

tasks.matching { it.name == "preBuild" }.configureEach {
  dependsOn(rootProject.tasks.named("verifyNoAppLogging"))
}
