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
import static org.junit.Assume.assumeTrue;

import io.camunda.zeebe.broker.exporter.util.ExternalExporter;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ExporterJarRepositoryTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final ExporterJarRepository jarRepository = new ExporterJarRepository();

  @Test
  public void shouldThrowExceptionOnLoadIfNotAJar() throws IOException {
    // given
    final var fake = temporaryFolder.newFile("fake-file");

    // then
    assertThatThrownBy(() -> jarRepository.load(fake.getAbsolutePath()))
        .isInstanceOf(ExporterJarLoadException.class);
  }

  @Test
  public void shouldThrowExceptionIfJarMissing() throws IOException {
    // given
    final var dummy = temporaryFolder.newFile("missing.jar");

    // when
    assertThat(dummy.delete()).isTrue();

    // then
    assertThatThrownBy(() -> jarRepository.load(dummy.getAbsolutePath()))
        .isInstanceOf(ExporterJarLoadException.class);
  }

  @Test
  public void shouldLoadClassLoaderForJar() throws IOException {
    // given
    final var dummy = temporaryFolder.newFile("readable.jar");

    // when (ignoring test if file cannot be set to be readable)
    assumeTrue(dummy.setReadable(true));

    // then
    assertThat(jarRepository.load(dummy.getAbsolutePath()))
        .isInstanceOf(ExporterJarClassLoader.class);
  }

  @Test
  public void shouldLoadClassLoaderCorrectlyOnlyOnce() throws Exception {
    // given
    final var exporterClass = ExternalExporter.createUnloadedExporterClass();
    final var jarFile = exporterClass.toJar(temporaryFolder.newFile("exporter.jar"));

    // when
    final var classLoader = jarRepository.load(jarFile.toPath());

    // then
    assertThat(jarRepository.load(jarFile.toPath())).isEqualTo(classLoader);
  }
}
