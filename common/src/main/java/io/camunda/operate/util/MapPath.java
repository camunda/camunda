/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * MapPath enables retrieving values from a Map<String, Object> recursive tree-like data structure by path
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
    Supplier<String> pathHead = () -> path.get(0);
    Supplier<List<String>> pathTail = () -> path.subList(1, path.size());
    Supplier<Object> headItem = () -> map.get(pathHead.get());

    return switch(path.size()) {
      case 0 -> Optional.empty();
      case 1 -> Optional.ofNullable(headItem.get()).map(Convertable::from);
      default -> Convertable.from(headItem.get())
        .<Map<String, Object>>to()
        .flatMap(map -> MapPath.from(map).getByPath(pathTail.get()));
    };
  }
}
