/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.optimize.util.types;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

public class MapUtil {
  public static <K, V> Map<K, V> fromPair(Pair<K, V> pair) {
    return Map.of(pair.getKey(), pair.getValue());
  }

  public static <K, V> Map<K, V> add(Map<K, V> map1, Map<K, V> map2) {
    final Map<K, V> result = new HashMap<>(map1);
    result.putAll(map2);
    return result;
  }
}
