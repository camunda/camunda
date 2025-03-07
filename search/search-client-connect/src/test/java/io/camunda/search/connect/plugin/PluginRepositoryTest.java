/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.search.engine.config.PluginConfiguration;
import io.camunda.plugin.search.header.DatabaseCustomHeaderSupplier;
import io.camunda.search.connect.plugin.util.TestDatabaseCustomHeaderSupplierImpl;
import io.camunda.zeebe.util.jar.ExternalJarClassLoader;
import io.camunda.zeebe.util.jar.ExternalJarLoadException;
import io.camunda.zeebe.util.jar.ExternalJarRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PluginRepositoryTest {

  @Test
  void shouldReturnUnmodifiableCollectionOfPlugins() throws IOException {
    final var initialPlugins =
        new LinkedHashMap<String, Class<? extends DatabaseCustomHeaderSupplier>>();
    initialPlugins.put("PLG1", TestDatabaseCustomHeaderSupplierImpl.class);
    final var jarRepository = Mockito.mock(ExternalJarRepository.class);
    final var basePath = Files.createTempDirectory("plg");
    final var repo = new PluginRepository(initialPlugins, jarRepository, basePath);

    final var plugins = repo.getPlugins();

    assertThat(plugins).hasSize(1);
    assertThatCode(() -> plugins.put("PLG2", TestDatabaseCustomHeaderSupplierImpl.class))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldNotLoadDuplicatePlugin() throws IOException {
    final var initialPlugins =
        new LinkedHashMap<String, Class<? extends DatabaseCustomHeaderSupplier>>();
    initialPlugins.put("PLG1", TestDatabaseCustomHeaderSupplierImpl.class);
    // the second one is with the same ID
    initialPlugins.put("PLG1", TestDatabaseCustomHeaderSupplierImpl.class);
    final var jarRepository = Mockito.mock(ExternalJarRepository.class);
    final var basePath = Files.createTempDirectory("plg");
    final var repo = new PluginRepository(initialPlugins, jarRepository, basePath);

    final var plugins = repo.getPlugins();

    assertThat(plugins).hasSize(1);
  }

  @Test
  void shouldLoadExternalPlugin() throws IOException, ClassNotFoundException {
    final var basePath = Files.createTempDirectory("plg");
    final var jarPath = basePath.resolve("plg.jar");
    final var config =
        new PluginConfiguration(
            "PLG1", TestDatabaseCustomHeaderSupplierImpl.class.getName(), jarPath);
    final var jarRepository = Mockito.mock(ExternalJarRepository.class);
    final var classLoader = Mockito.mock(ExternalJarClassLoader.class);
    when(jarRepository.load(jarPath)).thenReturn(classLoader);
    doReturn(TestDatabaseCustomHeaderSupplierImpl.class)
        .when(classLoader)
        .loadClass(config.className());
    final var repo = new PluginRepository(new LinkedHashMap<>(), jarRepository, basePath);

    repo.load(List.of(config));

    final var plugins = repo.getPlugins();
    assertThat(plugins).hasSize(1);
  }

  @Test
  void shouldLoadBasePathPlugin() throws IOException {
    final var config =
        new PluginConfiguration("PLG1", TestDatabaseCustomHeaderSupplierImpl.class.getName(), null);
    final var jarRepository = Mockito.mock(ExternalJarRepository.class);
    final var basePath = Files.createTempDirectory("plg");
    final var classLoader = Mockito.mock(ExternalJarClassLoader.class);
    when(jarRepository.load(basePath)).thenReturn(classLoader);
    final var repo = new PluginRepository(new LinkedHashMap<>(), jarRepository, basePath);

    repo.load(List.of(config));

    final var plugins = repo.getPlugins();
    assertThat(plugins).hasSize(1);
  }

  @Test
  void shouldRaiseExceptionWhenClassNotFound() throws IOException {
    final var config = new PluginConfiguration("PLG1", "io.camunda.UnknownClass", null);
    final var jarRepository = Mockito.mock(ExternalJarRepository.class);
    final var basePath = Files.createTempDirectory("plg");
    when(jarRepository.load(basePath)).thenThrow(ExternalJarLoadException.class);

    final var repo = new PluginRepository(new LinkedHashMap<>(), jarRepository, basePath);

    assertThatCode(() -> repo.load(List.of(config))).isInstanceOf(PluginLoadException.class);
  }

  @Test
  void shouldRaiseExceptionWhenLoadingWrongClass() throws IOException {
    final var config = new PluginConfiguration("PLG1", "io.camunda.UnknownClass", null);
    final var basePath = Files.createTempDirectory("plg");
    final var jarRepository = Mockito.mock(ExternalJarRepository.class);
    final var classLoader = Mockito.mock(ExternalJarClassLoader.class);
    when(jarRepository.load(basePath)).thenReturn(classLoader);

    final var repo = new PluginRepository(new LinkedHashMap<>(), jarRepository, basePath);

    assertThatCode(() -> repo.load(List.of(config))).isInstanceOf(PluginLoadException.class);
  }

  @Test
  void shouldCloseJarRepositoryWhenClosedExplicitly() throws Exception {
    final var jarRepository = Mockito.mock(ExternalJarRepository.class);
    final var basePath = Files.createTempDirectory("plg");
    final var repo = new PluginRepository(new LinkedHashMap<>(), jarRepository, basePath);

    repo.close();

    verify(jarRepository).close();
  }

  @Test
  void shouldCloseRepositoryWhenClosedViaTryWithResources() throws Exception {
    final var jarRepository = Mockito.mock(ExternalJarRepository.class);
    final var basePath = Files.createTempDirectory("plg");
    try (final var repo = new PluginRepository(new LinkedHashMap<>(), jarRepository, basePath)) {
      repo.getPlugins(); // no-op
    }

    verify(jarRepository).close();
  }
}
