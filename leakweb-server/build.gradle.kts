plugins {
    alias(libs.plugins.leakweb.jvm.library)
}

dependencies {
    api(project(":leakweb-model"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.nanohttpd)
    testImplementation(libs.kotlinx.coroutines.test)
}
