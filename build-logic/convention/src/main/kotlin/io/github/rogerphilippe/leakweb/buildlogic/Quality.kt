/*
 * Copyright (c) 2026 Roger Philippe
 */
package io.github.rogerphilippe.leakweb.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Applies Detekt and Spotless (ktlint + copyright header) to a module. */
internal fun Project.configureQuality() {
    pluginManager.apply("dev.detekt")
    pluginManager.apply("com.diffplug.spotless")

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig.set(true)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    }

    val ktlintVersion = libs.version("ktlint")
    extensions.configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint(ktlintVersion)
            licenseHeaderFile(rootProject.file("config/spotless/copyright.kt"))
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(ktlintVersion)
        }
    }
}
