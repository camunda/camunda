/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.impl.configuration.InterceptorCfg;
import io.camunda.zeebe.util.ReflectUtil;
import io.camunda.zeebe.util.jar.ExternalJarLoadException;
import io.camunda.zeebe.util.jar.ExternalJarRepository;
import io.grpc.ServerInterceptor;
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

/** Loads and holds references to the configured interceptors. */
public final class InterceptorRepository {
  private static final Logger LOGGER = Loggers.GATEWAY_LOGGER;

  private final ExternalJarRepository jarRepository;
  private final SortedMap<InterceptorId, Class<? extends ServerInterceptor>> interceptors;
  private final Path basePath;

  public InterceptorRepository() {
    this(
        new TreeMap<>(),
        new ExternalJarRepository(),
        Paths.get(Optional.ofNullable(System.getProperty("basedir")).orElse(".")));
  }

  InterceptorRepository(
      final SortedMap<InterceptorId, Class<? extends ServerInterceptor>> interceptors,
      final ExternalJarRepository jarRepository,
      final Path basePath) {
    this.interceptors = interceptors;
    this.jarRepository = jarRepository;
    this.basePath = basePath;
  }

  public SortedMap<InterceptorId, Class<? extends ServerInterceptor>> getInterceptors() {
    return Collections.unmodifiableSortedMap(interceptors);
  }

  public Stream<ServerInterceptor> instantiate() {
    return interceptors.entrySet().stream()
        .map(entry -> instantiate(entry.getKey().id, entry.getValue()));
  }

  public InterceptorRepository load(final List<? extends InterceptorCfg> configs) {
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

  Class<? extends ServerInterceptor> load(final int order, final InterceptorCfg config)
      throws ExternalJarLoadException {
    final ClassLoader classLoader;
    final Class<? extends ServerInterceptor> interceptorClass;
    final String id = config.getId();
    final InterceptorId interceptorId = new InterceptorId(id, order);

    if (interceptors.containsKey(interceptorId)) {
      return interceptors.get(interceptorId);
    }

    if (!config.isExternal()) {
      classLoader = getClass().getClassLoader();
    } else {
      final var jarPath = basePath.resolve(config.getJarPath());
      classLoader = jarRepository.load(jarPath);
    }

    try {
      final Class<?> specifiedClass = classLoader.loadClass(config.getClassName());
      interceptorClass = specifiedClass.asSubclass(ServerInterceptor.class);
    } catch (final ClassNotFoundException e) {
      throw new InterceptorLoadException(id, "cannot load specified class", e);
    } catch (final ClassCastException e) {
      throw new InterceptorLoadException(
          id, "specified class does not implement ServerInterceptor", e);
    }

    put(interceptorId, interceptorClass);
    return interceptorClass;
  }

  private void put(
      final InterceptorId id, final Class<? extends ServerInterceptor> interceptorClass) {
    interceptors.put(id, interceptorClass);
  }

  private ServerInterceptor instantiate(
      final String id, final Class<? extends ServerInterceptor> interceptorClass) {
    try {
      return ReflectUtil.newInstance(interceptorClass);
    } catch (final Exception e) {
      throw new InterceptorLoadException(id, "cannot instantiate via the default constructor", e);
    }
  }

  public record InterceptorId(String id, int order) implements Comparable<InterceptorId> {
    @Override
    public int compareTo(final InterceptorRepository.InterceptorId o) {
      return Comparator.comparingInt(InterceptorId::order).compare(this, o);
    }
  }
}
