/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.jar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.exporter.util.ExternalExporter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class ExporterJarRepositoryTest {
  private final ExporterJarRepository jarRepository = new ExporterJarRepository();

  @Test
  void shouldThrowExceptionOnLoadIfNotAJar(final @TempDir Path tempDir) throws IOException {
    // given
    final var fake = tempDir.resolve("fake-file");
    Files.writeString(fake, "foo");

    // then
    assertThatThrownBy(() -> jarRepository.load(fake)).isInstanceOf(ExporterJarLoadException.class);
  }

  @Test
  void shouldThrowExceptionIfJarMissing(final @TempDir Path tempDir) {
    // given
    final var dummy = tempDir.resolve("missing.jar");

    // then
    assertThatThrownBy(() -> jarRepository.load(dummy))
        .isInstanceOf(ExporterJarLoadException.class);
  }

  @Test
  void shouldLoadClassLoaderForJar(final @TempDir File tempDir) throws IOException {
    // given
    final var exporterClass = ExternalExporter.createUnloadedExporterClass();

    // when
    final var jarFile = exporterClass.toJar(new File(tempDir, "exporter.jar"));

    // then
    assertThat(jarRepository.load(jarFile.getAbsolutePath()))
        .isInstanceOf(ExporterJarClassLoader.class);
  }

  @Test
  void shouldLoadClassLoaderCorrectlyOnlyOnce(final @TempDir File tempDir) throws Exception {
    // given
    final var exporterClass = ExternalExporter.createUnloadedExporterClass();
    final var jarFile = exporterClass.toJar(new File(tempDir, "exporter.jar"));

    // when
    final var classLoader = jarRepository.load(jarFile.toPath());

    // then
    assertThat(jarRepository.load(jarFile.toPath())).isEqualTo(classLoader);
  }
}
