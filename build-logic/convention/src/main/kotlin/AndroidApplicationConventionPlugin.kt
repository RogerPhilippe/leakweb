/*
 * Copyright (c) 2026 Roger Philippe
 */
import com.android.build.api.dsl.ApplicationExtension
import io.github.rogerphilippe.leakweb.buildlogic.addUnitTestDependencies
import io.github.rogerphilippe.leakweb.buildlogic.configureQuality
import io.github.rogerphilippe.leakweb.buildlogic.javaVersion
import io.github.rogerphilippe.leakweb.buildlogic.libs
import io.github.rogerphilippe.leakweb.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Android application module (the sample app). */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                compileSdk = libs.version("compileSdk").toInt()
                defaultConfig {
                    minSdk = libs.version("minSdk").toInt()
                    targetSdk = libs.version("targetSdk").toInt()
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = javaVersion
                    targetCompatibility = javaVersion
                }
            }

            addUnitTestDependencies()
            configureQuality()
        }
    }
}
