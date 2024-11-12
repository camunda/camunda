/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * MapPath enables retrieving values from a Map<String, Object> recursive tree-like data structure
 * by path
 */
public class MapPath {
  private final Map<String, Object> map;

  public MapPath(Map<String, Object> map) {
    this.map = map;
  }

  public static MapPath from(Map<String, Object> map) {
    return new MapPath(map);
  }

  public Optional<Convertable> getByPath(String... path) {
    return getByPath(Arrays.asList(path));
  }

  public Optional<Convertable> getByPath(List<String> path) {
    final Supplier<String> pathHead = () -> path.get(0);
    final Supplier<List<String>> pathTail = () -> path.subList(1, path.size());
    final Supplier<Object> headItem = () -> map.get(pathHead.get());

    return switch (path.size()) {
      case 0 -> Optional.empty();
      case 1 -> Optional.ofNullable(headItem.get()).map(Convertable::from);
      default ->
          Convertable.from(headItem.get())
              .<Map<String, Object>>to()
              .flatMap(map -> MapPath.from(map).getByPath(pathTail.get()));
    };
  }
}
