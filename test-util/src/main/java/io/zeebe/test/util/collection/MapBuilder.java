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
import java.util.function.Consumer;

public class MapBuilder<T> {
  protected T returnValue;
  protected Consumer<Map<String, Object>> mapCallback;

  protected Map<String, Object> map;

  public MapBuilder(T returnValue, Consumer<Map<String, Object>> mapCallback) {
    this.returnValue = returnValue;
    this.mapCallback = mapCallback;
    this.map = new HashMap<>();
  }

  public MapBuilder<T> putAll(Map<String, Object> map) {
    this.map.putAll(map);
    return this;
  }

  public MapBuilder<T> put(String key, Object value) {
    this.map.put(key, value);
    return this;
  }

  public T done() {
    mapCallback.accept(map);
    return returnValue;
  }
}
