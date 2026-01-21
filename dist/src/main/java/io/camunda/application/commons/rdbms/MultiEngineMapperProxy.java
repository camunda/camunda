/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.db.rdbms.MultiEngineAware;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

public class MultiEngineMapperProxy<T> implements InvocationHandler {

  private final Class<T> mapperInterface;
  private final T defaultMapper;
  private final Map<String, T> engineMappers;
  private final String currentEngineId;

  private MultiEngineMapperProxy(
      final Class<T> mapperInterface,
      final T defaultMapper,
      final Map<String, T> engineMappers,
      final String currentEngineId) {
    this.mapperInterface = mapperInterface;
    this.defaultMapper = defaultMapper;
    this.engineMappers = engineMappers;
    this.currentEngineId = currentEngineId;
  }

  public static <T> T create(
      final Class<T> mapperInterface, final T defaultMapper, final Map<String, T> engineMappers) {
    return (T)
        Proxy.newProxyInstance(
            mapperInterface.getClassLoader(),
            new Class<?>[] {mapperInterface, MultiEngineAware.class},
            new MultiEngineMapperProxy<>(mapperInterface, defaultMapper, engineMappers, null));
  }

  @Override
  public Object invoke(final Object proxy, final Method method, final Object[] args)
      throws Throwable {
    if ("withEngine".equals(method.getName())
        && args != null
        && args.length == 1
        && args[0] instanceof String) {
      final String engineName = (String) args[0];
      return Proxy.newProxyInstance(
          mapperInterface.getClassLoader(),
          new Class<?>[] {mapperInterface, MultiEngineAware.class},
          new MultiEngineMapperProxy<>(mapperInterface, defaultMapper, engineMappers, engineName));
    }

    final T mapper;
    if (currentEngineId != null && engineMappers.containsKey(currentEngineId)) {
      mapper = engineMappers.get(currentEngineId);
    } else {
      mapper = defaultMapper;
    }

    return method.invoke(mapper, args);
  }
}
