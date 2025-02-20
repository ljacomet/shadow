package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.createDefaultFileTreeElement
import com.github.jengelman.gradle.plugins.shadow.internal.requireResourceAsStream
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer.Companion.create
import com.github.jengelman.gradle.plugins.shadow.util.testObjectFactory
import java.lang.reflect.ParameterizedType
import java.nio.file.Path
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.io.path.createTempFile
import kotlin.io.path.outputStream
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.RelativePath
import org.junit.jupiter.api.BeforeEach

abstract class BaseTransformerTest<T : Transformer> {
  protected lateinit var transformer: T
    private set

  protected val manifestTransformerContext: TransformerContext
    get() = TransformerContext(MANIFEST_NAME, requireResourceAsStream(MANIFEST_NAME))

  @BeforeEach
  open fun setup() {
    @Suppress("UNCHECKED_CAST")
    val clazz = (this::class.java.genericSuperclass as ParameterizedType).actualTypeArguments.first() as Class<T>
    transformer = clazz.create(testObjectFactory)
  }

  protected companion object {
    const val MANIFEST_NAME: String = "META-INF/MANIFEST.MF"

    fun Transformer.canTransformResource(path: String): Boolean {
      val element = createDefaultFileTreeElement(relativePath = RelativePath.parse(true, path))
      return canTransformResource(element)
    }

    fun readFrom(jarPath: Path, resourceName: String = MANIFEST_NAME): List<String> {
      return ZipFile(jarPath.toFile()).use { zip ->
        val entry = zip.getEntry(resourceName) ?: return emptyList()
        zip.getInputStream(entry).bufferedReader().readLines()
      }
    }

    fun doTransformAndGetTransformedPath(
      transformer: Transformer,
      preserveFileTimestamps: Boolean,
    ): Path {
      val testableZipPath = createTempFile("testable-zip-file-", ".jar")
      ZipOutputStream(testableZipPath.outputStream().buffered()).use { zipOutputStream ->
        transformer.modifyOutputStream(zipOutputStream, preserveFileTimestamps)
      }
      return testableZipPath
    }

    /**
     * NOTE: The Turkish locale has a usual case transformation for the letters "I" and "i", making it a prime
     * choice to test for improper case-less string comparisons.
     */
    fun setupTurkishLocale() {
      @Suppress("DEPRECATION")
      Locale.setDefault(Locale("tr"))
    }
  }
}
