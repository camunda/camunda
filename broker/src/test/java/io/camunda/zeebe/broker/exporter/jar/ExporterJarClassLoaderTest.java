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
import java.lang.reflect.Constructor;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import org.apache.logging.log4j.LogManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;

public final class ExporterJarClassLoaderTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void shouldLoadClassesPackagedInJar() throws Exception {
    final var exporterClass = ExternalExporter.createUnloadedExporterClass();
    final var jarFile = createExporterJar(exporterClass);
    final ExporterJarClassLoader classLoader = ExporterJarClassLoader.ofPath(jarFile.toPath());

    // when
    final Class<?> loadedClass = classLoader.loadClass(ExternalExporter.EXPORTER_CLASS_NAME);

    // then
    final Constructor<?> constructor = loadedClass.getConstructor();
    assertThat(loadedClass.getDeclaredField("FOO").get(loadedClass)).isEqualTo("bar");
    assertThat(constructor.newInstance()).isInstanceOf(Exporter.class);
  }

  @Test
  public void shouldLoadSystemClassesFromSystemClassLoader() throws Exception {
    final var exporterClass = ExternalExporter.createUnloadedExporterClass();
    final var jarFile = createExporterJar(exporterClass);
    final ExporterJarClassLoader classLoader = ExporterJarClassLoader.ofPath(jarFile.toPath());

    // when
    final Class<?> loadedClass = classLoader.loadClass(String.class.getCanonicalName());

    // then
    assertThat(loadedClass).isEqualTo(String.class);
  }

  @Test
  public void shouldLoadZbExporterClassesFromSystemClassLoader() throws Exception {
    final var exporterClass = ExternalExporter.createUnloadedExporterClass();
    final var jarFile = createExporterJar(exporterClass);
    final ExporterJarClassLoader classLoader = ExporterJarClassLoader.ofPath(jarFile.toPath());

    // when
    final Class<?> loadedClass = classLoader.loadClass(Exporter.class.getCanonicalName());

    // then
    assertThat(loadedClass).isEqualTo(Exporter.class);
  }

  @Test
  public void shouldLoadSL4JClassesFromSystemClassLoader() throws Exception {
    final var exporterClass = ExternalExporter.createUnloadedExporterClass();
    final var jarFile = createExporterJar(exporterClass);
    final ExporterJarClassLoader classLoader = ExporterJarClassLoader.ofPath(jarFile.toPath());

    // when
    final Class<?> loadedClass = classLoader.loadClass(Logger.class.getCanonicalName());

    // then
    assertThat(loadedClass).isEqualTo(Logger.class);
  }

  @Test
  public void shouldLoadLog4JClassesFromSystemClassLoader() throws Exception {
    final var exporterClass = ExternalExporter.createUnloadedExporterClass();
    final var jarFile = createExporterJar(exporterClass);
    final ExporterJarClassLoader classLoader = ExporterJarClassLoader.ofPath(jarFile.toPath());

    // when
    final Class<?> loadedClass = classLoader.loadClass(LogManager.class.getCanonicalName());

    // then
    assertThat(loadedClass).isEqualTo(LogManager.class);
  }

  private File createExporterJar(final Unloaded<Exporter> exporterClass) throws IOException {
    final var jarFile = temporaryFolder.newFile("exporter.jar");
    return exporterClass.toJar(jarFile);
  }
}
