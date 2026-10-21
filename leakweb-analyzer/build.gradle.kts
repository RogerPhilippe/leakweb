plugins {
    alias(libs.plugins.leakweb.jvm.library)
}

dependencies {
    api(project(":leakweb-model"))
}
