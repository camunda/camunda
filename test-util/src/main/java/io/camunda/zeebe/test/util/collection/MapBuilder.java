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
import java.util.function.Consumer;

public final class MapBuilder<T> {
  protected final T returnValue;
  protected final Consumer<Map<String, Object>> mapCallback;

  protected final Map<String, Object> map;

  public MapBuilder(final T returnValue, final Consumer<Map<String, Object>> mapCallback) {
    this.returnValue = returnValue;
    this.mapCallback = mapCallback;
    map = new HashMap<>();
  }

  public MapBuilder<T> putAll(final Map<String, Object> map) {
    this.map.putAll(map);
    return this;
  }

  public MapBuilder<T> put(final String key, final Object value) {
    map.put(key, value);
    return this;
  }

  public T done() {
    mapCallback.accept(map);
    return returnValue;
  }
}
