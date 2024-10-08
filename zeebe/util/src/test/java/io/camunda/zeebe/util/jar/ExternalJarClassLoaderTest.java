/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.jar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.util.jar.ExternalService.Service;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;

@Execution(ExecutionMode.CONCURRENT)
final class ExternalJarClassLoaderTest {

  @Test
  void shouldLoadClassesPackagedInJar(final @TempDir File tempDir) throws Exception {
    final var serviceClass = ExternalService.createUnloadedExporterClass();
    final var jarFile = serviceClass.toJar(new File(tempDir, "service.jar"));
    final var classLoader = ExternalJarClassLoader.ofPath(jarFile.toPath());

    // when
    final var loadedClass = classLoader.loadClass(ExternalService.CLASS_NAME);

    // then
    final var constructor = loadedClass.getConstructor();
    assertThat(loadedClass.getDeclaredField("FOO").get(loadedClass)).isEqualTo("bar");
    assertThat(constructor.newInstance()).isInstanceOf(Service.class);
  }

  @Test
  void shouldUseSystemClassLoaderAsFallback(final @TempDir File tempDir)
      throws IOException, ClassNotFoundException {
    final var serviceClass = ExternalService.createUnloadedExporterClass();
    final var jarFile = serviceClass.toJar(new File(tempDir, "service.jar"));
    final var classLoader = ExternalJarClassLoader.ofPath(jarFile.toPath());

    // when
    final var loadedClass = classLoader.loadClass(Logger.class.getCanonicalName());

    // then
    assertThat(loadedClass).isEqualTo(Logger.class);
    assertThat(classLoader.getParent())
        .isEqualTo(getClass().getClassLoader())
        .isEqualTo(ClassLoader.getSystemClassLoader());
  }

  @Test
  void shouldLoadResourceFiles(final @TempDir File tempDir) throws Exception {
    final var testResourceValue = "test-value";
    final var resourceName = "test-resource.txt";
    final var jarFile = new File(tempDir, "with-resource.jar");
    try (final var fileOutputStream = new FileOutputStream(jarFile)) {
      try (final var jarOutputStream = new JarOutputStream(fileOutputStream)) {
        jarOutputStream.putNextEntry(new JarEntry(resourceName));
        jarOutputStream.write(testResourceValue.getBytes(StandardCharsets.UTF_8));
        jarOutputStream.closeEntry();
      }
    }
    final var classLoader = ExternalJarClassLoader.ofPath(jarFile.toPath());

    // when
    final var storedValue =
        new String(classLoader.getResourceAsStream(resourceName).readAllBytes());

    // then
    assertThat(storedValue).isEqualTo(testResourceValue);
  }

  @ParameterizedTest
  @NullSource // used to simulate the default case
  @ValueSource(booleans = {true, false})
  void shouldCloseWhenInvoked(final Boolean param, final @TempDir File tempDir) throws IOException {
    // given
    final var serviceClass = ExternalService.createUnloadedExporterClass();
    final var jarFile = serviceClass.toJar(new File(tempDir, "service.jar"));
    final var classLoader = ExternalJarClassLoader.ofPath(jarFile.toPath());

    // when
    if (param == null) {
      classLoader.close();
    } else {
      classLoader.close(param);
    }

    // then
    assertThatCode(() -> classLoader.loadClass(ExternalService.CLASS_NAME))
        .isInstanceOf(ClassNotFoundException.class);
  }

  @Test
  void shouldCloseWhenTryWithResourcesCompleted(final @TempDir File tempDir) throws Exception {
    // given
    final var serviceClass = ExternalService.createUnloadedExporterClass();
    final var jarFile = serviceClass.toJar(new File(tempDir, "service.jar"));

    final URLClassLoader classLoaderRef;

    // when
    try (final var classLoaderRefInternal = ExternalJarClassLoader.ofPath(jarFile.toPath())) {
      classLoaderRef = classLoaderRefInternal;
    }

    // then
    assertThatCode(() -> classLoaderRef.loadClass(ExternalService.CLASS_NAME))
        .isInstanceOf(ClassNotFoundException.class);
  }
}
