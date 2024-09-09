/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.jar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class ExternalJarRepositoryTest {
  private final ExternalJarRepository jarRepository = new ExternalJarRepository();

  @Test
  void shouldThrowExceptionOnLoadIfNotAJar(final @TempDir Path tempDir) throws IOException {
    // given
    final var fake = tempDir.resolve("fake-file");
    Files.writeString(fake, "foo");

    // then
    assertThatThrownBy(() -> jarRepository.load(fake)).isInstanceOf(ExternalJarLoadException.class);
  }

  @Test
  void shouldThrowExceptionIfJarMissing(final @TempDir Path tempDir) {
    // given
    final var dummy = tempDir.resolve("missing.jar");

    // then
    assertThatThrownBy(() -> jarRepository.load(dummy))
        .isInstanceOf(ExternalJarLoadException.class);
  }

  @Test
  void shouldLoadClassLoaderForJar(final @TempDir File tempDir) throws IOException {
    // given
    final var serviceClass = ExternalService.createUnloadedExporterClass();

    // when
    final var jarFile = serviceClass.toJar(new File(tempDir, "service.jar"));

    // then
    assertThat(jarRepository.load(jarFile.getAbsolutePath()))
        .isInstanceOf(ExternalJarClassLoader.class);
  }

  @Test
  void shouldLoadClassLoaderCorrectlyOnlyOnce(final @TempDir File tempDir) throws Exception {
    // given
    final var serviceClass = ExternalService.createUnloadedExporterClass();
    final var jarFile = serviceClass.toJar(new File(tempDir, "service.jar"));

    // when
    final var classLoader = jarRepository.load(jarFile.toPath());

    // then
    assertThat(jarRepository.load(jarFile.toPath())).isEqualTo(classLoader);
  }

  @Test
  void shouldCloseClassLoadersWithExplicitCall() throws Exception {
    final var loadedJars = new HashMap<Path, ExternalJarClassLoader>();
    final var externalJarClassLoaderMock = mock(ExternalJarClassLoader.class);
    loadedJars.put(mock(Path.class), externalJarClassLoaderMock);
    final var repo = new ExternalJarRepository(loadedJars);

    repo.close();

    verify(externalJarClassLoaderMock).close(false);
  }

  @Test
  void shouldCloseClassLoadersWithTryWithResources() throws Exception {
    final var loadedJars = new HashMap<Path, ExternalJarClassLoader>();
    final var externalJarClassLoaderMock = mock(ExternalJarClassLoader.class);
    loadedJars.put(mock(Path.class), externalJarClassLoaderMock);

    try (final var repo = new ExternalJarRepository(loadedJars)) {
      repo.getJars(); // no-op
    }

    verify(externalJarClassLoaderMock).close(false);
  }
}
