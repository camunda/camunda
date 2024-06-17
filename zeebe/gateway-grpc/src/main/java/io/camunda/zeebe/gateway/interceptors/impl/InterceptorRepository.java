/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.agrona.LangUtil;
import org.slf4j.Logger;

/** Loads and holds references to the configured interceptors. */
public final class InterceptorRepository {
  private static final Logger LOGGER = Loggers.GATEWAY_LOGGER;

  private final ExternalJarRepository jarRepository;
  private final List<InterceptorElement> interceptors;
  private final Path basePath;

  public InterceptorRepository() {
    this(
        new ArrayList<>(),
        new ExternalJarRepository(),
        Paths.get(Optional.ofNullable(System.getProperty("basedir")).orElse(".")));
  }

  InterceptorRepository(
      final List<InterceptorElement> interceptors,
      final ExternalJarRepository jarRepository,
      final Path basePath) {
    this.interceptors = interceptors;
    this.jarRepository = jarRepository;
    this.basePath = basePath;
  }

  public List<InterceptorElement> getInterceptors() {
    return interceptors;
  }

  public Stream<ServerInterceptor> instantiate() {
    return interceptors.stream().map(entry -> instantiate(entry.id, entry.clazz));
  }

  public InterceptorRepository load(final List<? extends InterceptorCfg> configs) {
    configs.forEach(
        config -> {
          try {
            load(config);
          } catch (final Exception e) {
            LOGGER.debug("Failed to load interceptor {} with config {}", config.getId(), config);
            LangUtil.rethrowUnchecked(e);
          }
        });

    return this;
  }

  Class<? extends ServerInterceptor> load(final InterceptorCfg config)
      throws ExternalJarLoadException {
    final ClassLoader classLoader;
    final Class<? extends ServerInterceptor> interceptorClass;
    final String id = config.getId();

    final var existingInterceptor =
        interceptors.stream()
            .filter(interceptorElement -> interceptorElement.id.equals(id))
            .findFirst();
    if (existingInterceptor.isPresent()) {
      return existingInterceptor.get().clazz;
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

    final InterceptorElement interceptorElement = new InterceptorElement(id, interceptorClass);
    interceptors.add(interceptorElement);

    return interceptorClass;
  }

  private ServerInterceptor instantiate(
      final String id, final Class<? extends ServerInterceptor> interceptorClass) {
    try {
      return ReflectUtil.newInstance(interceptorClass);
    } catch (final Exception e) {
      throw new InterceptorLoadException(id, "cannot instantiate via the default constructor", e);
    }
  }

  public record InterceptorElement(String id, Class<? extends ServerInterceptor> clazz) {}
}
