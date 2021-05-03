/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.collection;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public final class MapFactoryBuilder<A, T> {
  protected final T returnValue;
  protected final Consumer<Function<A, Map<String, Object>>> factoryCallback;

  protected BiConsumer<A, Map<String, Object>> manipulationChain = (a, m) -> {};

  public MapFactoryBuilder(
      final T returnValue, final Consumer<Function<A, Map<String, Object>>> factoryCallback) {
    this.returnValue = returnValue;
    this.factoryCallback = factoryCallback;
  }

  public MapFactoryBuilder<A, T> allOf(final Function<A, Map<String, Object>> otherMap) {
    manipulationChain = manipulationChain.andThen((a, m) -> m.putAll(otherMap.apply(a)));
    return this;
  }

  public MapFactoryBuilder<A, T> put(final String key, final Object value) {
    manipulationChain = manipulationChain.andThen((a, m) -> m.put(key, value));
    return this;
  }

  public MapFactoryBuilder<A, T> put(final String key, final Function<A, Object> valueFunction) {
    manipulationChain = manipulationChain.andThen((a, m) -> m.put(key, valueFunction.apply(a)));
    return this;
  }

  public T done() {
    factoryCallback.accept(
        (a) -> {
          final Map<String, Object> map = new HashMap<>();
          manipulationChain.accept(a, map);
          return map;
        });
    return returnValue;
  }
}
