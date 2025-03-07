/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.plugin;

import io.camunda.db.search.engine.config.PluginConfiguration;
import io.camunda.plugin.search.header.DatabaseCustomHeaderSupplier;
import io.camunda.zeebe.util.ReflectUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.jar.ExternalJarLoadException;
import io.camunda.zeebe.util.jar.ExternalJarRepository;
import io.camunda.zeebe.util.jar.ThreadContextUtil;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.SequencedMap;
import java.util.stream.Stream;
import org.agrona.LangUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads and holds references to the configured plugins.
 */
public final class PluginRepository implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(PluginRepository.class);

  private final ExternalJarRepository jarRepository;
  private final LinkedHashMap<String, Class<? extends DatabaseCustomHeaderSupplier>> plugins;
  private final Path basePath;

  public PluginRepository() {
    this(
        new LinkedHashMap<>(),
        new ExternalJarRepository(),
        Paths.get(Optional.ofNullable(System.getProperty("basedir")).orElse(".")));
  }

  PluginRepository(
      final LinkedHashMap<String, Class<? extends DatabaseCustomHeaderSupplier>> plugins,
      final ExternalJarRepository jarRepository,
      final Path basePath) {
    this.plugins = plugins;
    this.jarRepository = jarRepository;
    this.basePath = basePath;
  }

  @VisibleForTesting
  SequencedMap<String, Class<? extends DatabaseCustomHeaderSupplier>> getPlugins() {
    return Collections.unmodifiableSequencedMap(plugins);
  }

  public boolean isEmpty() {
    return plugins.isEmpty();
  }

  public CompatHttpRequestInterceptor asRequestInterceptor() {
    return PluginRepositoryInterceptor.ofRepository(this);
  }

  public PluginRepository load(final SequencedCollection<PluginConfiguration> configs) {
    if (configs == null || configs.isEmpty()) {
      return this;
    }

    configs.forEach(
        config -> {
          try {
            load(config);
          } catch (final Exception e) {
            LOGGER.debug("Failed to load interceptor {} with config {}", config.id(), config);
            LangUtil.rethrowUnchecked(e);
          }
        });

    return this;
  }

  @Override
  public void close() throws Exception {
    try {
      jarRepository.close();
    } catch (final Exception e) {
      LOGGER.warn(
          "Failed to close external JAR repository; may result in dangling file descriptors", e);
    }
  }

  private void load(final PluginConfiguration config) throws ExternalJarLoadException {
    final ClassLoader classLoader;
    final Class<? extends DatabaseCustomHeaderSupplier> interceptorClass;
    final String id = config.id();

    if (plugins.containsKey(id)) {
      return;
    }

    if (!config.isExternal()) {
      classLoader = getClass().getClassLoader();
    } else {
      final var jarPath = basePath.resolve(config.jarPath());
      // the class loader will be closed along with the JAR repository
      classLoader = jarRepository.load(jarPath);
    }

    try {
      final Class<?> specifiedClass = classLoader.loadClass(config.className());
      interceptorClass = specifiedClass.asSubclass(DatabaseCustomHeaderSupplier.class);
    } catch (final ClassNotFoundException e) {
      throw new PluginLoadException(id, "cannot load specified class", e);
    } catch (final ClassCastException e) {
      throw new PluginLoadException(
          id,
          "specified class does not implement " + DatabaseCustomHeaderSupplier.class.getName(),
          e);
    }

    plugins.put(id, interceptorClass);
  }

  Stream<DatabaseCustomHeaderSupplier> instantiatePlugins() {
    return plugins.entrySet().stream()
        .map(entry -> instantiateSinglePlugin(entry.getKey(), entry.getValue()));
  }

  private DatabaseCustomHeaderSupplier instantiateSinglePlugin(
      final String id, final Class<? extends DatabaseCustomHeaderSupplier> interceptorClass) {
    try {
      return instrumented(ReflectUtil.newInstance(interceptorClass));
    } catch (final Exception e) {
      throw new PluginLoadException(id, "cannot instantiate via the default constructor", e);
    }
  }

  private DatabaseCustomHeaderSupplier instrumented(final DatabaseCustomHeaderSupplier supplier) {
    return () ->
        ThreadContextUtil.supplyWithClassLoader(
            supplier::getSearchDatabaseCustomHeader, supplier.getClass().getClassLoader());
  }
}
