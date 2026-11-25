/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.impl.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * The state stores canonical keys as they are and finds the keys under a prefix as a string range.
 * The range must hold exactly the keys at or under the prefix on whole segments, so it is checked
 * against a plain filter over every key that a small alphabet can form.
 */
final class KeyPathTest {

  /** Covers a digit, a letter, '-' (which sorts just below the separator) and the separator. */
  private static final List<String> SEGMENTS = List.of("a", "b", "0", "-", "a-", "ab", "-a");

  @Test
  void shouldFindExactlyKeysAtOrUnderEveryPrefix() {
    // given
    final var keys = allKeys();
    final var map = new TreeMap<String, String>();
    keys.forEach(key -> map.put(key, key));

    for (final var prefix : keys) {
      // when
      final var found = KeyPath.atOrUnder(map, prefix).keySet();

      // then
      final var expected =
          keys.stream()
              .filter(key -> key.equals(prefix) || key.startsWith(prefix + "."))
              .sorted()
              .toList();
      assertThat(found).as("keys at or under '%s'", prefix).containsExactlyElementsOf(expected);
    }
  }

  @Test
  void shouldFindAllKeysForEmptyPrefix() {
    // given
    final var map = new TreeMap<String, String>();
    allKeys().forEach(key -> map.put(key, key));

    // when - then
    assertThat(KeyPath.atOrUnder(map, "").keySet()).containsExactlyElementsOf(map.keySet());
  }

  @Test
  void shouldListPathsFromFirstSegmentToKey() {
    // when - then
    assertThat(KeyPath.pathsTo("a.b-c.d")).containsExactly("a", "a.b-c", "a.b-c.d");
    assertThat(KeyPath.pathsTo("a")).containsExactly("a");
  }

  /** Every key of one to three segments from the segment list: 7 + 49 + 343 keys. */
  private static List<String> allKeys() {
    final var keys = new ArrayList<String>();
    var level = List.of("");
    for (int depth = 1; depth <= 3; depth++) {
      final var next = new ArrayList<String>();
      for (final var parent : level) {
        for (final var segment : SEGMENTS) {
          next.add(parent.isEmpty() ? segment : parent + "." + segment);
        }
      }
      keys.addAll(next);
      level = next;
    }
    return keys;
  }
}
