plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("jvmLibrary") {
            id = libs.plugins.leakweb.jvm.library.get().pluginId
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.leakweb.android.library.get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = libs.plugins.leakweb.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
    }
}
