/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.poc.core;

import io.camunda.zeebe.cluster.gossip.GlobalKey;
import io.camunda.zeebe.cluster.gossip.GossipKey;
import io.camunda.zeebe.cluster.gossip.MemberKey;
import io.camunda.zeebe.cluster.gossip.MergeStrategy.LastWriterWins;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Keys registered on first use. Not thread-safe; callers hold the gossiper's lock. */
final class KeyRegistry {

  private final Map<String, GossipKey<?>> keys = new HashMap<>();

  /**
   * Registers the key unless a compatible key with the same name is registered already.
   *
   * @return whether the key was newly registered
   * @throws IllegalStateException if the name is registered with an incompatible descriptor
   */
  boolean register(final GossipKey<?> key) {
    final var existing = keys.putIfAbsent(key.name(), key);
    if (existing == null) {
      return true;
    }
    if (!describe(existing).equals(describe(key))) {
      throw new IllegalStateException(
          "Expected key '"
              + key.name()
              + "' to be used as registered ("
              + describe(existing)
              + "), but it was used as "
              + describe(key));
    }
    return false;
  }

  @Nullable GossipKey<?> get(final String name) {
    return keys.get(name);
  }

  private static String describe(final GossipKey<?> key) {
    return switch (key) {
      case final MemberKey<?> ignored -> "MEMBER";
      case final GlobalKey<?> global ->
          global.mergeStrategy() instanceof LastWriterWins<?>
              ? "GLOBAL, last writer wins"
              : "GLOBAL, custom merge";
    };
  }
}
