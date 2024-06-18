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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.agrona.LangUtil;
import org.slf4j.Logger;

/** Loads and holds references to the configured filters. */
public final class FilterRepository {
  private static final Logger LOGGER = Loggers.GATEWAY_LOGGER;

  private final ExternalJarRepository jarRepository;
  private final List<FilterElement> filters;
  private final Path basePath;

  public FilterRepository() {
    this(
        new ArrayList<>(),
        new ExternalJarRepository(),
        Paths.get(Optional.ofNullable(System.getProperty("basedir")).orElse(".")));
  }

  public FilterRepository(
      final List<FilterElement> filters,
      final ExternalJarRepository jarRepository,
      final Path basePath) {
    this.filters = filters;
    this.jarRepository = jarRepository;
    this.basePath = basePath;
  }

  public List<FilterElement> getFilters() {
    return filters;
  }

  public Stream<Filter> instantiate() {
    return filters.stream().map(entry -> instantiate(entry.id, entry.clazz));
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

    final var existingFilter =
        filters.stream().filter(filterElement -> filterElement.id.equals(id)).findFirst();
    if (existingFilter.isPresent()) {
      return existingFilter.get().clazz;
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

    final FilterElement filterElement = new FilterElement(id, filterClass);
    filters.add(filterElement);

    return filterClass;
  }

  private Filter instantiate(final String id, final Class<? extends Filter> filterClass) {
    try {
      return ReflectUtil.newInstance(filterClass);
    } catch (final Exception e) {
      throw new FilterLoadException(id, "cannot instantiate via the default constructor", e);
    }
  }

  public record FilterElement(String id, Class<? extends Filter> clazz) {}
}
