plugins {
    alias(libs.plugins.leakweb.android.library)
}

android {
    namespace = "io.github.rogerphilippe.leakweb"
}

dependencies {
    api(project(":leakweb-api"))
    implementation(project(":leakweb-model"))
    implementation(project(":leakweb-analyzer"))
    implementation(project(":leakweb-server"))
}
