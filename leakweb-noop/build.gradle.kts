plugins {
    alias(libs.plugins.leakweb.android.library)
}

android {
    namespace = "io.github.rogerphilippe.leakweb.noop"
}

dependencies {
    api(project(":leakweb-api"))
}
