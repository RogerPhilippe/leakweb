plugins {
    alias(libs.plugins.leakweb.jvm.library)
}

dependencies {
    api(project(":leakweb-model"))
    implementation(project(":leakweb-watcher"))
    implementation(libs.shark.android)
}

tasks.test {
    // Tests dump the test JVM heap and analyze it with Shark.
    maxHeapSize = "1g"
}
