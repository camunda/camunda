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
import java.io.IOException;
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
}
