plugins {
  kotlin("multiplatform")
  id("com.android.library")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.compose")
  kotlin("plugin.serialization")
}

kotlin {
  androidTarget()

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material)
        implementation(compose.material3)
        implementation(compose.materialIconsExtended)
        implementation(compose.ui)

        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

        val ktorVersion = "2.3.12"
        implementation("io.ktor:ktor-client-core:$ktorVersion")
        implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
        implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
      }
    }

    val androidMain by getting {
      dependencies {
        val ktorVersion = "2.3.12"
        implementation("io.ktor:ktor-client-cio:$ktorVersion")
        implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
        implementation("io.ktor:ktor-client-logging:$ktorVersion")
        implementation("io.ktor:ktor-client-websockets:$ktorVersion")
        implementation("org.mozilla:rhino:1.7.14")
        implementation("com.squareup.okhttp3:okhttp:4.12.0")
        implementation("io.coil-kt:coil-compose:2.6.0")
        implementation("com.google.zxing:core:3.5.3")
        implementation("app.cash.quickjs:quickjs-android:0.9.2")
        implementation("androidx.core:core-ktx:1.13.1")
        implementation("androidx.activity:activity-compose:1.9.2")

        val media3Version = "1.3.1"
        implementation("androidx.media3:media3-exoplayer:$media3Version")
        implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
        implementation("androidx.media3:media3-ui:$media3Version")
      }
    }
  }
}

android {
  namespace = "dtv.mobile.shared"
  compileSdk = 36
  buildToolsVersion = "36.1.0"

  defaultConfig {
    minSdk = 26
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}
