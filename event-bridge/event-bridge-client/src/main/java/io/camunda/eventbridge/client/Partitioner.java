/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import java.util.Arrays;

/**
 * Maps a record key to a target partition of a topic. Used by {@link
 * EventBridgeClient#publishToTopic(String, int, String, byte[])} to route a keyed record to a
 * partition when the caller does not pick one explicitly.
 *
 * <p>Partitions are <em>1-indexed</em>: a valid result is in {@code [1, partitionCount]}.
 */
@FunctionalInterface
public interface Partitioner {

  /**
   * Returns the target partition (1-indexed) for {@code key} on {@code topic}, given {@code
   * partitionCount} partitions.
   *
   * @param topic the topic being published to
   * @param key the record key, or {@code null} for a keyless record
   * @param partitionCount the number of partitions in the topic (at least 1)
   * @return a partition id in {@code [1, partitionCount]}
   */
  int partition(String topic, byte[] key, int partitionCount);

  /**
   * The default hash partitioner: {@code 1 + Math.floorMod(hash, partitionCount)}, matching the
   * routing used across the Event Bridge apps. A {@code null} key hashes to {@code 0}, which maps
   * to partition {@code 1}.
   */
  static Partitioner defaultHash() {
    return (topic, key, partitionCount) -> {
      final int hash = key == null ? 0 : Arrays.hashCode(key);
      return 1 + Math.floorMod(hash, partitionCount);
    };
  }
}
