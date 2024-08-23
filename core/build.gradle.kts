import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    id("org.mozilla.rust-android-gradle.rust-android")
    id("com.android.library")
    id("com.google.devtools.ksp")
    kotlin("android")
    id("kotlin-parcelize")
}

setupCore()

android {
    namespace = "com.github.shadowsocks.core"

    buildFeatures {
        buildConfig = true
        aidl = true
    }

    defaultConfig {
        consumerProguardFiles("proguard-rules.pro")

        externalNativeBuild.ndkBuild {
            abiFilters("arm64-v8a")
            arguments("-j${Runtime.getRuntime().availableProcessors()}")
        }

        ksp {
            arg("room.incremental", "true")
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    externalNativeBuild.ndkBuild.path("src/main/jni/Android.mk")

    sourceSets.getByName("androidTest") {
        assets.setSrcDirs(assets.srcDirs + files("$projectDir/schemas"))
    }
}

cargo {
    module = "src/main/rust/shadowsocks-rust"
    libname = "sslocal"
    targets = listOf("arm64")
    profile = findProperty("CARGO_PROFILE")?.toString() ?: currentFlavor
    extraCargoBuildArguments = listOf("--bin", libname!!)
    features {
        noDefaultBut(arrayOf(
                         "logging",
                         "local-flow-stat",
                         "local-dns",
                         "aead-cipher-2022"
                     ))
    }
    exec = { spec, toolchain ->
            run {
                try {
                    Runtime.getRuntime().exec("python3 -V >/dev/null 2>&1")
                    spec.environment("RUST_ANDROID_GRADLE_PYTHON_COMMAND", "python3")
                    project.logger.lifecycle("Python 3 detected.")
                } catch (e: java.io.IOException) {
                    project.logger.lifecycle("No python 3 detected.")
                    try {
                        Runtime.getRuntime().exec("python -V >/dev/null 2>&1")
                        spec.environment("RUST_ANDROID_GRADLE_PYTHON_COMMAND", "python")
                        project.logger.lifecycle("Python detected.")
                    } catch (e: java.io.IOException) {
                        throw GradleException("No any python version detected. You should install the python first to compile project.")
                    }
                }
                spec.environment("RUST_ANDROID_GRADLE_LINKER_WRAPPER_PY", "$projectDir/$module/../linker-wrapper.py")
                spec.environment("RUST_ANDROID_GRADLE_TARGET", "target/${toolchain.target}/$profile/lib$libname.so")
            }
    }
}

tasks.whenTaskAdded {
    when (name) {
        "mergeDebugJniLibFolders", "mergeReleaseJniLibFolders" -> dependsOn("cargoBuild")
    }
}

tasks.register<Exec>("cargoClean") {
    executable("cargo")     // cargo.cargoCommand
    args("clean")
    workingDir("$projectDir/${cargo.module}")
}
tasks.clean.dependsOn("cargoClean")

dependencies {
    val coroutinesVersion = "1.6.4"
    val roomVersion = "2.6.1"
    val workVersion = "2.9.1"

    api(project(":plugin"))
    api("androidx.core:core-ktx:1.13.1")
    api("com.google.android.material:material:1.12.0")

    api("androidx.lifecycle:lifecycle-livedata-core-ktx:$lifecycleVersion")
    api("androidx.preference:preference:1.2.1")
    api("androidx.room:room-runtime:$roomVersion")
    api("androidx.work:work-multiprocess:$workVersion")
    api("androidx.work:work-runtime-ktx:$workVersion")
    api("com.google.android.gms:play-services-oss-licenses:17.0.0")
    api("com.google.code.gson:gson:2.11.0")
    api("com.jakewharton.timber:timber:5.0.1")
    api("dnsjava:dnsjava:3.6.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$coroutinesVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.1.5")
}
