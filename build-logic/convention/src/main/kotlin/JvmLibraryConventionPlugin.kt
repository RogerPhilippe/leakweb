/*
 * Copyright (c) 2026 Roger Philippe
 */
import io.github.rogerphilippe.leakweb.buildlogic.addUnitTestDependencies
import io.github.rogerphilippe.leakweb.buildlogic.configureQuality
import io.github.rogerphilippe.leakweb.buildlogic.javaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Pure JVM module (no android.* imports): api, model, analyzer, server. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            extensions.configure<KotlinJvmProjectExtension> {
                jvmToolchain(javaVersion.majorVersion.toInt())
            }

            addUnitTestDependencies()
            configureQuality()
        }
    }
}
