pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // ── 依赖仓库映射（仅影响 org.jetbrains.* 依赖，不影响插件解析）──
        // 现象：KSP2 的 kotlinBuildToolsApiClasspath 在解析
        // org.jetbrains.kotlin:kotlin-compiler-embeddable 时会走到
        // github.com/JetBrains/kotlin/releases/download/...，该网关频繁返回
        // 504 Gateway Time-out，导致 CI 构建在 4 分钟后失败。
        // 同一个 artifact 在 Maven 中央仓库可用（已用 HTTP 200 验证）。
        // 这里前置一个带 content 过滤的镜像，把 org.jetbrains.* 的解析锁在国内
        // 镜像 + 中央仓库，避免命中 GitHub release 资源。
        // 注意：只过滤 org.jetbrains 组，KSP 等 com.google.* 插件仍走原来的
        // pluginManagement 仓库（阿里云缺 KSP marker，不能整体前置）。
        maven {
            url = uri("https://maven.aliyun.com/repository/central")
            content {
                includeGroupByRegex("org\\.jetbrains\\..*")
            }
        }
        maven {
            url = uri("https://repo1.maven.org/maven2")
            content {
                includeGroupByRegex("org\\.jetbrains\\..*")
            }
        }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "EBookReader"
include(":app")
include(":benchmark")
include(":modules:rhino")
include(":legado-engine")
include(":third_party:subsampling-scale-image-view")

include(":third_party:backdrop")
include(":third_party:word-converters")
