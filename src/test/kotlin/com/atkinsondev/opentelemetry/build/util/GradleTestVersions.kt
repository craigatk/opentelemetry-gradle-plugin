package com.atkinsondev.opentelemetry.build.util

import org.gradle.util.GradleVersion
import java.util.stream.Stream

object GradleTestVersions {
    @JvmStatic
    fun versions(): Stream<String> = Stream.of("8.4", "8.5", "8.7", "8.10.2", GradleVersion.current().version)

    @JvmStatic
    fun currentVersion(): Stream<String> = Stream.of(GradleVersion.current().version)
}
