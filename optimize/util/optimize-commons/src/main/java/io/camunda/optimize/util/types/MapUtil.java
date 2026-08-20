/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.util.types;

import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;

public class MapUtil {
  public static <K, V> Map<K, V> createFromPair(final Pair<K, V> pair) {
    return Map.of(pair.getKey(), pair.getValue());
  }

  public static <K, V> Map<K, V> combineUniqueMaps(final Map<K, V> map1, final Map<K, V> map2) {
    return Stream.of(map1, map2)
        .flatMap(m -> m.entrySet().stream())
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  public static <K, V> Collector<Pair<K, V>, ?, Map<K, V>> pairCollector() {
    return Collectors.toMap(Pair::getKey, Pair::getValue);
  }
}
