plugins {
    alias(libs.plugins.leakweb.android.library)
}

android {
    namespace = "io.github.rogerphilippe.leakweb"

    testOptions {
        // Lets unit tests instantiate framework classes (e.g. Activity) from the android.jar stubs.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":leakweb-api"))
    implementation(project(":leakweb-model"))
    implementation(project(":leakweb-watcher"))
    implementation(project(":leakweb-analyzer"))
    implementation(project(":leakweb-server"))
    implementation(libs.androidx.startup.runtime)
    implementation(libs.kotlinx.coroutines.core)

    // Fragments and ViewModels are watched only when the app already uses AndroidX Fragment / Lifecycle.
    compileOnly(libs.androidx.fragment)
    compileOnly(libs.androidx.lifecycle.viewmodel)
    testImplementation(libs.androidx.fragment)
    testImplementation(libs.androidx.lifecycle.viewmodel)
    testImplementation(libs.kotlinx.coroutines.test)
}
