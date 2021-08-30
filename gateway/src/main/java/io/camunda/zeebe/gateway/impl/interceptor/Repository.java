/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.interceptor;

import io.camunda.zeebe.gateway.Interceptor;
import io.camunda.zeebe.gateway.impl.configuration.InterceptorCfg;
import io.camunda.zeebe.gateway.impl.interceptor.jar.InterceptorJarRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class Repository {

  private final Map<String, Descriptor> interceptors = new HashMap<>();
  private final InterceptorJarRepository jarRepository = new InterceptorJarRepository();

  public void load(final String id, final InterceptorCfg config) {
    if (interceptors.containsKey(id)) {
      // already loaded
      return;
    }

    final var classLoader = jarRepository.load(config.getJarPath());
    final Class<?> specifiedClass;
    try {
      specifiedClass = classLoader.loadClass(config.getClassName());
    } catch (final ClassNotFoundException e) {
      final var message =
          String.format("Unable to load class %s for interceptor %s", config.getClassName(), id);
      throw new RuntimeException(message, e);
    }
    final Class<? extends Interceptor> interceptorClass =
        specifiedClass.asSubclass(Interceptor.class);
    interceptors.put(id, new Descriptor(id, interceptorClass));
  }

  public void forEach(final BiConsumer<String, Descriptor> action) {
    interceptors.forEach(action);
  }
}
