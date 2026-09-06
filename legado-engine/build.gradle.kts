import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("com.google.devtools.ksp")
}

android {
    // 保持与上游一致：让引擎内 120 个源文件里的 io.legado.app.R / BuildConfig 自然解析
    namespace = "io.legado.app"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all",
        )
    }
}

dependencies {
    api(project(":modules:rhino"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.collection:collection-ktx:1.4.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // Room：引擎自带独立库 legado_engine.db，与 Lumi 现有 Room v12 物理隔离
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // 书源解析核心三件套
    api("org.jsoup:jsoup:1.16.2")
    api("cn.wanghaomiao:JsoupXpath:2.5.5")
    api("com.jayway.jsonpath:json-path:3.0.0")
    api("com.google.code.gson:gson:2.11.0")

    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("cn.hutool:hutool-crypto:5.8.22")
    implementation("cn.hutool:hutool-core:5.8.22")
    implementation("org.apache.commons:commons-text:1.12.0")
    implementation("com.github.liuyueyi.quick-chinese-transfer:quick-transfer-core:0.2.17")

    implementation("com.louiscad.splitties:splitties-appctx:3.0.0")
    implementation("com.louiscad.splitties:splitties-systemservices:3.0.0")
    // Archive extraction used by book/source JS helpers.
    implementation("me.zhanghai.android.libarchive:library:1.1.4")
    // @Language("js") is source-retention metadata only, but must be resolvable by Kotlin.
    implementation("org.jetbrains:annotations:24.1.0")
}