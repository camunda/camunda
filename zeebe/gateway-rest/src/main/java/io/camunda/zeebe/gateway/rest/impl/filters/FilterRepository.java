/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.impl.filters;

import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.impl.configuration.FilterCfg;
import io.camunda.zeebe.util.ReflectUtil;
import io.camunda.zeebe.util.jar.ExternalJarLoadException;
import io.camunda.zeebe.util.jar.ExternalJarRepository;
import jakarta.servlet.Filter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.agrona.LangUtil;
import org.slf4j.Logger;

/**
 * Loads and holds references to the configured filters.
 *
 * <p>Please port any changes to this code to the {@code zeebe-gateway-grpc} module's {@code
 * io.camunda.zeebe.gateway.interceptors.impl.InterceptorRepository} as well.
 */
public final class FilterRepository {
  private static final Logger LOGGER = Loggers.GATEWAY_LOGGER;

  private final ExternalJarRepository jarRepository;
  private final Map<String, Class<? extends Filter>> filters;
  private final Path basePath;

  public FilterRepository() {
    this(
        new LinkedHashMap<>(),
        new ExternalJarRepository(),
        Paths.get(Optional.ofNullable(System.getProperty("basedir")).orElse(".")));
  }

  public FilterRepository(
      final Map<String, Class<? extends Filter>> filters,
      final ExternalJarRepository jarRepository,
      final Path basePath) {
    this.filters = filters;
    this.jarRepository = jarRepository;
    this.basePath = basePath;
  }

  public Map<String, Class<? extends Filter>> getFilters() {
    return filters;
  }

  public Stream<Filter> instantiate() {
    return filters.entrySet().stream().map(entry -> instantiate(entry.getKey(), entry.getValue()));
  }

  public FilterRepository load(final List<? extends FilterCfg> configs) {
    configs.forEach(
        config -> {
          try {
            load(config);
          } catch (final Exception e) {
            LOGGER.debug("Failed to load filter {} with config {}", config.getId(), config);
            LangUtil.rethrowUnchecked(e);
          }
        });

    return this;
  }

  public Class<? extends Filter> load(final FilterCfg config) throws ExternalJarLoadException {
    final ClassLoader classLoader;
    final Class<? extends Filter> filterClass;
    final String id = config.getId();

    if (filters.containsKey(id)) {
      return filters.get(id);
    }

    if (!config.isExternal()) {
      classLoader = getClass().getClassLoader();
    } else {
      final var jarPath = basePath.resolve(config.getJarPath());
      classLoader = jarRepository.load(jarPath);
    }

    try {
      final Class<?> specifiedClass = classLoader.loadClass(config.getClassName());
      filterClass = specifiedClass.asSubclass(Filter.class);
    } catch (final ClassNotFoundException e) {
      throw new FilterLoadException(id, "cannot load specified class", e);
    } catch (final ClassCastException e) {
      throw new FilterLoadException(id, "specified class does not implement Filter", e);
    }

    filters.put(id, filterClass);

    return filterClass;
  }

  private Filter instantiate(final String id, final Class<? extends Filter> filterClass) {
    try {
      return ReflectUtil.newInstance(filterClass);
    } catch (final Exception e) {
      throw new FilterLoadException(id, "cannot instantiate via the default constructor", e);
    }
  }
}
