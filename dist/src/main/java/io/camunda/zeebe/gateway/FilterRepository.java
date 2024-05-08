/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import io.camunda.zeebe.gateway.impl.configuration.FilterCfg;
import io.camunda.zeebe.gateway.interceptors.impl.InterceptorLoadException;
import io.camunda.zeebe.util.ReflectUtil;
import io.camunda.zeebe.util.jar.ExternalJarLoadException;
import io.camunda.zeebe.util.jar.ExternalJarRepository;
import jakarta.servlet.Filter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.agrona.LangUtil;
import org.slf4j.Logger;

public class FilterRepository {
  private static final Logger LOGGER = Loggers.GATEWAY_LOGGER;

  private final ExternalJarRepository jarRepository;
  private final SortedMap<FilterId, Class<? extends Filter>> filters;
  private final Path basePath;

  public FilterRepository() {
    this(
        new TreeMap<>(),
        new ExternalJarRepository(),
        Paths.get(Optional.ofNullable(System.getProperty("basedir")).orElse(".")));
  }

  FilterRepository(
      final SortedMap<FilterId, Class<? extends Filter>> filters,
      final ExternalJarRepository jarRepository,
      final Path basePath) {
    this.filters = filters;
    this.jarRepository = jarRepository;
    this.basePath = basePath;
  }

  public SortedMap<FilterId, Class<? extends Filter>> getFilters() {
    return Collections.unmodifiableSortedMap(filters);
  }

  public Stream<Filter> instantiate() {
    return filters.entrySet().stream()
        .map(entry -> instantiate(entry.getKey().id, entry.getValue()));
  }

  public FilterRepository load(final List<? extends FilterCfg> configs) {
    IntStream.range(0, configs.size())
        .forEach(
            i -> {
              final var config = configs.get(i);
              try {
                load(i, config);
              } catch (final Exception e) {
                LOGGER.debug(
                    "Failed to load interceptor {} with config {}", config.getId(), config);
                LangUtil.rethrowUnchecked(e);
              }
            });

    return this;
  }

  Class<? extends Filter> load(final int order, final FilterCfg config)
      throws ExternalJarLoadException {
    final ClassLoader classLoader;
    final Class<? extends Filter> filterClass;
    final String id = config.getId();
    final FilterId filterId = new FilterRepository.FilterId(id, order);

    if (filters.containsKey(filterId)) {
      return filters.get(filterId);
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
      throw new InterceptorLoadException(id, "cannot load specified class", e);
    } catch (final ClassCastException e) {
      throw new InterceptorLoadException(
          id, "specified class does not implement ServerInterceptor", e);
    }

    put(filterId, filterClass);
    return filterClass;
  }

  private void put(final FilterId id, final Class<? extends Filter> filterClass) {
    filters.put(id, filterClass);
  }

  private Filter instantiate(final String id, final Class<? extends Filter> filterClass) {
    try {
      return ReflectUtil.newInstance(filterClass);
    } catch (final Exception e) {
      throw new InterceptorLoadException(id, "cannot instantiate via the default constructor", e);
    }
  }

  public record FilterId(String id, int order) implements Comparable<FilterId> {
    @Override
    public int compareTo(final FilterId o) {
      return Comparator.comparingInt(FilterId::order).compare(this, o);
    }
  }
}
