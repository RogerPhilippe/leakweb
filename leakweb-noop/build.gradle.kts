plugins {
    alias(libs.plugins.leakweb.android.library)
}

android {
    namespace = "io.github.rogerphilippe.leakweb.noop"
}

dependencies {
    api(project(":leakweb-api"))
}

// The compatibility test compares the compiled LeakWeb class of leakweb-android and of this module.
tasks.withType<Test>().configureEach {
    val classesPath = "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
    dependsOn(":leakweb-android:compileDebugKotlin", ":leakweb-noop:compileDebugKotlin")
    systemProperty(
        "leakweb.androidClasses",
        project(":leakweb-android")
            .layout.buildDirectory
            .dir(classesPath)
            .get()
            .asFile.path,
    )
    systemProperty(
        "leakweb.noopClasses",
        layout.buildDirectory
            .dir(classesPath)
            .get()
            .asFile.path,
    )
}
