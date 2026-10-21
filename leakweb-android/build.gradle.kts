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

    // Fragments are watched only when the app already uses AndroidX Fragment.
    compileOnly(libs.androidx.fragment)
    testImplementation(libs.androidx.fragment)
}
