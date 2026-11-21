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
        implementation("org.jetbrains.compose.runtime:runtime:1.9.3")
        implementation("org.jetbrains.compose.foundation:foundation:1.9.3")
        implementation("org.jetbrains.compose.material:material:1.9.3")
        implementation("org.jetbrains.compose.material3:material3:1.9.0")
        implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
        implementation("org.jetbrains.compose.ui:ui:1.9.3")

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
        implementation("io.ktor:ktor-client-websockets:$ktorVersion")
        implementation("io.ktor:ktor-server-core:$ktorVersion")
        implementation("io.ktor:ktor-server-cio:$ktorVersion")
        implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
        implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
        implementation("org.mozilla:rhino:1.7.14")
        implementation("com.squareup.okhttp3:okhttp:4.12.0")
        implementation("io.coil-kt:coil-compose:2.6.0")
        implementation("com.google.zxing:core:3.5.3")
        implementation("com.journeyapps:zxing-android-embedded:4.3.0")
        implementation("app.cash.quickjs:quickjs-android:0.9.2")
        implementation("androidx.core:core-ktx:1.13.1")
        implementation("androidx.activity:activity-compose:1.9.2")

        val media3Version = "1.3.1"
        implementation("androidx.media3:media3-exoplayer:$media3Version")
        implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
        implementation("androidx.media3:media3-ui:$media3Version")
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
      }
    }

    val androidInstrumentedTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation("org.jetbrains.compose.ui:ui-test:1.9.3")
        implementation("androidx.test:runner:1.6.2")
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
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}
