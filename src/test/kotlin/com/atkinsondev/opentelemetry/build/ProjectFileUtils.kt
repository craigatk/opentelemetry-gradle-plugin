package com.atkinsondev.opentelemetry.build

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories

fun createSrcDirectoryAndClassFile(projectRootDirPath: Path) {
    val srcPath = Paths.get(projectRootDirPath.absolutePathString(), "src/main/kotlin").createDirectories()

    val sourceFileContents = """
            fun foo() = "bar"
    """.trimIndent()
    File(srcPath.toFile(), "foo.kt").writeText(sourceFileContents)
}

fun createTestDirectoryAndClassFile(projectRootDirPath: Path) {
    val testPath = Paths.get(projectRootDirPath.absolutePathString(), "src/test/kotlin").createDirectories()

    val testFileContents = """
            import org.junit.jupiter.api.Test
            
            class FooTest {
                @Test
                fun `foo should return bar`() {
                    assert(foo() == "bar")
                }
            }
    """.trimIndent()
    File(testPath.toFile(), "FooTest.kt").writeText(testFileContents)
}
