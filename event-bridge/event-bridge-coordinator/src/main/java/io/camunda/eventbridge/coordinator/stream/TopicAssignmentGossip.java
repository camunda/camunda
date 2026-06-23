/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.coordinator.stream;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cross-node propagation of the topic registry from the coordinator (the registry shard's leader)
 * to every broker's reconciler.
 *
 * <p>The registry is the authoritative <em>desired state</em>, and it is small, so it is broadcast
 * whole — not as a diff. Each broker reconciles its local topic Raft groups against it
 * idempotently, so a missed or duplicated broadcast is harmless: the next one converges. This is
 * the anti-entropy half of the reconciliation loop (see Option 1: coordinator broadcasts, brokers
 * reconcile).
 */
public final class TopicAssignmentGossip {

  /** Cluster-communication subject the registry-shard leader broadcasts the topic registry on. */
  public static final String SUBJECT = "event-bridge-topic-assignment";

  private TopicAssignmentGossip() {}

  /** Encodes the registry as one {@code name;partitionCount;replicationFactor;status} line each. */
  public static byte[] encode(final Map<String, TopicMetadata> topics) {
    final var sb = new StringBuilder();
    topics.forEach((name, meta) -> sb.append(name).append(';').append(meta.encode()).append('\n'));
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  /** Decodes a broadcast payload back into the topic registry (insertion-ordered). */
  public static Map<String, TopicMetadata> decode(final byte[] payload) {
    final Map<String, TopicMetadata> topics = new LinkedHashMap<>();
    if (payload == null || payload.length == 0) {
      return topics;
    }
    for (final var line : new String(payload, StandardCharsets.UTF_8).split("\n")) {
      if (line.isBlank()) {
        continue;
      }
      final var sep = line.indexOf(';');
      topics.put(line.substring(0, sep), TopicMetadata.decode(line.substring(sep + 1)));
    }
    return topics;
  }

  /**
   * Sink for the encoded topic registry. On the coordinator it is backed by cluster broadcast (plus
   * local self-delivery, since broadcast excludes the sender); on a broker it feeds the reconciler.
   */
  @FunctionalInterface
  public interface Publisher {
    void publish(byte[] payload);
  }
}
