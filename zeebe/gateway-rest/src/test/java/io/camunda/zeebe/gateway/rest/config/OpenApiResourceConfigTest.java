/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.rest.util.OpenApiYamlLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApiResourceConfigTest {

  @TempDir Path tempDir;

  @Test
  void shouldResolveInstalledSpecPathFromLibDir() throws Exception {
    // given
    final Path installDir = tempDir.resolve("camunda");
    final Path spec = installDir.resolve(OpenApiYamlLoader.DEFAULT_SPEC_PATH);
    Files.createDirectories(spec.getParent());
    Files.writeString(spec, "openapi: 3.0.3\ninfo: {title: t, version: '1'}\npaths: {}\n");

    final Path libDir = installDir.resolve("lib");
    Files.createDirectories(libDir);

    // when
    final String resolved = OpenApiResourceConfig.resolveSpecPath(libDir);

    // then
    assertThat(resolved).isEqualTo(spec.toString());
  }

  @Test
  void shouldResolveInstalledSpecPathFromInstallDir() throws Exception {
    // given
    final Path installDir = tempDir.resolve("camunda");
    final Path spec = installDir.resolve(OpenApiYamlLoader.DEFAULT_SPEC_PATH);
    Files.createDirectories(spec.getParent());
    Files.writeString(spec, "openapi: 3.0.3\ninfo: {title: t, version: '1'}\npaths: {}\n");

    // when
    final String resolved = OpenApiResourceConfig.resolveSpecPath(installDir);

    // then
    assertThat(resolved).isEqualTo(spec.toString());
  }

  @Test
  void shouldFallbackToClasspathWhenInstalledSpecMissing() {
    // given
    final Path appDir = tempDir.resolve("somewhere");

    // when
    final String resolved = OpenApiResourceConfig.resolveSpecPath(appDir);

    // then
    assertThat(resolved).isEqualTo(OpenApiYamlLoader.DEFAULT_CLASSPATH_SPEC_PATH);
  }
}
