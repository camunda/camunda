/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.impl.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.NavigableMap;
import java.util.SequencedMap;

/**
 * Prefix matching on canonical keys, which the state stores as they are. A key is at or under a
 * prefix if it equals it or starts with the prefix and a separator, so matching is on whole
 * segments: {@code a.b} matches {@code a.b} and {@code a.b.c}, not {@code a.bc} or {@code a.b-x}.
 *
 * <p>In plain string order, the keys starting with {@code p.} are exactly the keys from {@code p.}
 * (inclusive) to {@code p/} (exclusive): {@code /} is the character right after the separator, so
 * no other key fits between the two bounds. The prefix itself sorts before that range, so it is
 * looked up on its own. This needs the map's natural {@link String} order.
 */
public final class KeyPath {

  private static final char SEPARATOR = '.';
  private static final char AFTER_SEPARATOR = (char) (SEPARATOR + 1);

  private KeyPath() {}

  /**
   * Returns the entries at or under the prefix, in key order: the prefix itself, if present, then
   * the range of keys under it. The empty prefix returns the whole map.
   */
  public static <V> SequencedMap<String, V> atOrUnder(
      final NavigableMap<String, V> map, final String prefix) {
    if (prefix.isEmpty()) {
      return map;
    }
    final var result = new LinkedHashMap<String, V>();
    final var exact = map.get(prefix);
    if (exact != null) {
      result.put(prefix, exact);
    }
    result.putAll(map.subMap(prefix + SEPARATOR, true, prefix + AFTER_SEPARATOR, false));
    return result;
  }

  /** Returns the key and each of its ancestor paths, from the first segment down to the key. */
  public static List<String> pathsTo(final String key) {
    final var paths = new ArrayList<String>();
    for (int i = key.indexOf(SEPARATOR); i >= 0; i = key.indexOf(SEPARATOR, i + 1)) {
      paths.add(key.substring(0, i));
    }
    paths.add(key);
    return paths;
  }
}
