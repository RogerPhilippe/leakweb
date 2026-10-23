// Top-level build file. Plugins are declared here (not applied) so that the
// convention plugins in build-logic and the modules share a single classpath.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.binary.compatibility.validator)
}

// Only the modules that make up the public API are validated; the others are implementation details.
apiValidation {
    ignoredProjects.addAll(
        listOf("leakweb-model", "leakweb-watcher", "leakweb-analyzer", "leakweb-server", "sample"),
    )
}
