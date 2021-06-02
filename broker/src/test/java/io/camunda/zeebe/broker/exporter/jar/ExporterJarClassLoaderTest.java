/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.jar;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.exporter.util.ExternalExporter;
import io.camunda.zeebe.exporter.api.Exporter;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;

@Execution(ExecutionMode.CONCURRENT)
final class ExporterJarClassLoaderTest {

  @Test
  void shouldLoadClassesPackagedInJar(final @TempDir File tempDir) throws Exception {
    final var exporterClass = ExternalExporter.createUnloadedExporterClass();
    final var jarFile = exporterClass.toJar(new File(tempDir, "exporter.jar"));
    final var classLoader = ExporterJarClassLoader.ofPath(jarFile.toPath());

    // when
    final var loadedClass = classLoader.loadClass(ExternalExporter.EXPORTER_CLASS_NAME);

    // then
    final var constructor = loadedClass.getConstructor();
    assertThat(loadedClass.getDeclaredField("FOO").get(loadedClass)).isEqualTo("bar");
    assertThat(constructor.newInstance()).isInstanceOf(Exporter.class);
  }

  @Test
  void shouldUseSystemClassLoaderAsFallback(final @TempDir File tempDir)
      throws IOException, ClassNotFoundException {
    final var exporterClass = ExternalExporter.createUnloadedExporterClass();
    final var jarFile = exporterClass.toJar(new File(tempDir, "exporter.jar"));
    final var classLoader = ExporterJarClassLoader.ofPath(jarFile.toPath());

    // when
    final var loadedClass = classLoader.loadClass(Logger.class.getCanonicalName());

    // then
    assertThat(loadedClass).isEqualTo(Logger.class);
    assertThat(classLoader.getParent())
        .isEqualTo(getClass().getClassLoader())
        .isEqualTo(ClassLoader.getSystemClassLoader());
  }
}
