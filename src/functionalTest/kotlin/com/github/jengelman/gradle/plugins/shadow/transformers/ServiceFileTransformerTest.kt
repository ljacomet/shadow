package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.getContent
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ServiceFileTransformerTest : BaseTransformerTest() {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun serviceResourceTransformer(shortSyntax: Boolean) {
    val config = if (shortSyntax) {
      """
        dependencies {
          ${implementationFiles(buildJarOne(), buildJarTwo())}
        }
        $shadowJar {
          mergeServiceFiles {
            exclude 'META-INF/services/com.acme.*'
          }
        }
      """.trimIndent()
    } else {
      transform<ServiceFileTransformer>(
        dependenciesBlock = implementationFiles(buildJarOne(), buildJarTwo()),
        transformerBlock = """
          exclude 'META-INF/services/com.acme.*'
        """.trimIndent(),
      )
    }
    projectScriptPath.appendText(config)

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      getContent(ENTRY_SERVICES_SHADE).isEqualTo(CONTENT_ONE_TWO)
      getContent(ENTRY_SERVICES_FOO).isEqualTo("one")
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun serviceResourceTransformerAlternatePath(shortSyntax: Boolean) {
    val one = buildJarOne {
      insert(ENTRY_FOO_SHADE, CONTENT_ONE)
    }
    val two = buildJarTwo {
      insert(ENTRY_FOO_SHADE, CONTENT_TWO)
    }
    val config = if (shortSyntax) {
      """
        dependencies {
          ${implementationFiles(one, two)}
        }
        $shadowJar {
          mergeServiceFiles("META-INF/foo")
        }
      """.trimIndent()
    } else {
      transform<ServiceFileTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = """
          path = 'META-INF/foo'
        """.trimIndent(),
      )
    }
    projectScriptPath.appendText(config)

    run(shadowJarTask)

    val content = outputShadowJar.use { it.getContent(ENTRY_FOO_SHADE) }
    assertThat(content).isEqualTo(CONTENT_ONE_TWO)
  }

  @Test
  fun serviceResourceTransformerRelocation() {
    val one = buildJarOne {
      insert(
        "META-INF/services/java.sql.Driver",
        """
          oracle.jdbc.OracleDriver
          org.apache.hive.jdbc.HiveDriver
        """.trimIndent(),
      )
      insert(
        "META-INF/services/org.apache.axis.components.compiler.Compiler",
        "org.apache.axis.components.compiler.Javac",
      )
      insert(
        "META-INF/services/org.apache.commons.logging.LogFactory",
        "org.apache.commons.logging.impl.LogFactoryImpl",
      )
    }
    val two = buildJarTwo {
      insert(
        "META-INF/services/java.sql.Driver",
        """
          org.apache.derby.jdbc.AutoloadedDriver
          com.mysql.jdbc.Driver
        """.trimIndent(),
      )
      insert(
        "META-INF/services/org.apache.axis.components.compiler.Compiler",
        "org.apache.axis.components.compiler.Jikes",
      )
      insert(
        "META-INF/services/org.apache.commons.logging.LogFactory",
        "org.mortbay.log.Factory",
      )
    }

    projectScriptPath.appendText(
      """
        dependencies {
          ${implementationFiles(one, two)}
        }
        $shadowJar {
          mergeServiceFiles()
          relocate("org.apache", "myapache") {
            exclude 'org.apache.axis.components.compiler.Jikes'
            exclude 'org.apache.commons.logging.LogFactory'
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      getContent("META-INF/services/java.sql.Driver").isEqualTo(
        """
          oracle.jdbc.OracleDriver
          myapache.hive.jdbc.HiveDriver
          myapache.derby.jdbc.AutoloadedDriver
          com.mysql.jdbc.Driver
        """.trimIndent(),
      )
      getContent("META-INF/services/myapache.axis.components.compiler.Compiler").isEqualTo(
        """
          myapache.axis.components.compiler.Javac
          org.apache.axis.components.compiler.Jikes
        """.trimIndent(),
      )
      getContent("META-INF/services/org.apache.commons.logging.LogFactory").isEqualTo(
        """
          myapache.commons.logging.impl.LogFactoryImpl
          org.mortbay.log.Factory
        """.trimIndent(),
      )
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/70",
    "https://github.com/GradleUp/shadow/issues/71",
  )
  @Test
  fun applyTransformersToProjectResources() {
    val servicesBarEntry = "META-INF/services/foo.Bar"
    val one = buildJarOne {
      insert(servicesBarEntry, CONTENT_ONE)
    }.toUri().toURL().path
    localRepo.module("foo", "bar", "1.0") {
      buildJar {
        insert(servicesBarEntry, CONTENT_TWO)
      }
    }.publish()

    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'foo:bar:1.0'
          implementation files('$one')
        }
        $shadowJar {
          mergeServiceFiles()
        }
      """.trimIndent(),
    )
    path("src/main/resources/$servicesBarEntry").writeText(CONTENT_THREE)

    run(shadowJarTask)

    val content = outputShadowJar.use { it.getContent(servicesBarEntry) }
    assertThat(content).isEqualTo(CONTENT_THREE + "\n" + CONTENT_ONE_TWO)
  }
}
