/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.jar;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.jar.ExternalService.Service;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
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
    final String testResourceValue = "test-value";
    final String resourceName = "test-resource.txt";
    final File jarFile = new File(tempDir, "with-resource.jar");
    try (final FileOutputStream fileOutputStream = new FileOutputStream(jarFile)) {
      try (final JarOutputStream jarOutputStream = new JarOutputStream(fileOutputStream)) {
        jarOutputStream.putNextEntry(new JarEntry(resourceName));
        jarOutputStream.write(testResourceValue.getBytes(StandardCharsets.UTF_8));
        jarOutputStream.closeEntry();
      }
    }
    final var classLoader = ExternalJarClassLoader.ofPath(jarFile.toPath());

    // when
    final String storedValue =
        new String(classLoader.getResourceAsStream(resourceName).readAllBytes());

    // then
    assertThat(storedValue).isEqualTo(testResourceValue);
  }
}
