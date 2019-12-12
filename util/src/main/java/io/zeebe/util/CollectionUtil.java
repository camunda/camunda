/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CollectionUtil {

  public static <K, V> void addToMapOfLists(final Map<K, List<V>> map, final K key, final V value) {
    List<V> list = map.get(key);
    if (list == null) {
      list = new ArrayList<>();
      map.put(key, list);
    }

    list.add(value);
  }
}
