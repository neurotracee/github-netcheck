import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

// 版本号统一四段：主.次.修订.补丁（与 Windows 端完全一致，见 CONTRIBUTING.md）。
// versionCode 由 versionName 直接算出来，只改这一个字符串，不会再出现两处对不上。
// 映射：主*1000000 + 次*10000 + 修订*100 + 补丁（例：1.0.1.0 → 1000100）。
val appVersionName = "1.1.4.1"
val appVersionCode = appVersionName
    .split(".")
    .map { it.trim().toInt() }
    .let { it[0] * 1_000_000 + it[1] * 10_000 + it[2] * 100 + it[3] }

android {
    namespace = "com.neurotracee.netcheck"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.neurotracee.netcheck"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
        // 纯 Kotlin 实现，没有 native 库，不需要按 ABI 拆包
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // v1（JAR）签名留作老启动器/老 ROM 的兜底，v2 + v3 是主力；
                // v3 才是 9.0 以后系统校验的那一层，只留 v2 在部分 ROM 上会被判「签名信息不完整」。
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module"
            )
        }
    }

    sourceSets {
        getByName("main") {
            // 图标源文件与 README 共用一份，放在仓库根目录 assets/icon
            assets.srcDir(rootProject.file("../assets"))
        }
    }

    // 工程目录含中文时，测试工作进程按系统默认编码（GBK）读取类路径会找不到测试类，
    // 表现为一堆 ClassNotFoundException。显式指定 UTF-8 即可。
    testOptions {
        unitTests {
            all {
                it.jvmArgs("-Dfile.encoding=UTF-8")
                it.systemProperty("file.encoding", "UTF-8")
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.24")
}
