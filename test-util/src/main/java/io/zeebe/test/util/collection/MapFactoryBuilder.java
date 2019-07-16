/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.collection;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class MapFactoryBuilder<A, T> {
  protected T returnValue;
  protected Consumer<Function<A, Map<String, Object>>> factoryCallback;

  protected BiConsumer<A, Map<String, Object>> manipulationChain = (a, m) -> {};

  public MapFactoryBuilder(
      T returnValue, Consumer<Function<A, Map<String, Object>>> factoryCallback) {
    this.returnValue = returnValue;
    this.factoryCallback = factoryCallback;
  }

  public MapFactoryBuilder<A, T> allOf(Function<A, Map<String, Object>> otherMap) {
    manipulationChain = manipulationChain.andThen((a, m) -> m.putAll(otherMap.apply(a)));
    return this;
  }

  public MapFactoryBuilder<A, T> put(String key, Object value) {
    manipulationChain = manipulationChain.andThen((a, m) -> m.put(key, value));
    return this;
  }

  public MapFactoryBuilder<A, T> put(String key, Function<A, Object> valueFunction) {
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
