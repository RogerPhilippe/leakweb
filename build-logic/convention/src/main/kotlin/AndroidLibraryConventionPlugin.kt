/*
 * Copyright (c) 2026 Roger Philippe
 */
import com.android.build.api.dsl.LibraryExtension
import io.github.rogerphilippe.leakweb.buildlogic.addUnitTestDependencies
import io.github.rogerphilippe.leakweb.buildlogic.configureQuality
import io.github.rogerphilippe.leakweb.buildlogic.javaVersion
import io.github.rogerphilippe.leakweb.buildlogic.libs
import io.github.rogerphilippe.leakweb.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Android library module (Kotlin is built into AGP 9). */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                compileSdk = libs.version("compileSdk").toInt()
                defaultConfig {
                    minSdk = libs.version("minSdk").toInt()
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
