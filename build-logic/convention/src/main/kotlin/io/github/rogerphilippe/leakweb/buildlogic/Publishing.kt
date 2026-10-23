/*
 * Copyright (c) 2026 Roger Philippe
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.github.rogerphilippe.leakweb.buildlogic

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create

private const val REPOSITORY_URL = "https://github.com/RogerPhilippe/leakweb"

/** Applies `maven-publish` and fills the POM; [component] is `java` (JVM modules) or `release` (Android). */
internal fun Project.configurePublishing(component: String) {
    pluginManager.apply("maven-publish")

    // The Android component only exists after the Android plugin has configured its variants.
    afterEvaluate {
        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("release") {
                    from(components.getByName(component))
                    artifactId = project.name
                    pom {
                        name.set(project.name)
                        description.set("Memory leak detection for Android with a web interface.")
                        url.set(REPOSITORY_URL)
                        licenses {
                            license {
                                name.set("GNU Affero General Public License v3.0 or later")
                                url.set("https://www.gnu.org/licenses/agpl-3.0.txt")
                            }
                        }
                        developers {
                            developer {
                                name.set("Roger Philippe Pereira")
                                url.set("https://github.com/RogerPhilippe")
                            }
                        }
                        scm {
                            url.set(REPOSITORY_URL)
                            connection.set("scm:git:$REPOSITORY_URL.git")
                        }
                    }
                }
            }
        }
    }
}
