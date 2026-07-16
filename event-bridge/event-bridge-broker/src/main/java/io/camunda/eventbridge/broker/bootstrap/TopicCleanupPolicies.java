/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.bootstrap;

import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata;
import io.camunda.eventbridge.protocol.request.coordination.CleanupPolicy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread-safe topic&rarr;cleanupPolicy cache fed by the same observed metadata-registry snapshots
 * as {@link TopicPartitionCounts}, read by {@link TopicReconciler} to resolve a topic's cleanup
 * policy for the change-coordinator-driven join path.
 *
 * <p>The {@code reconcile} path already receives the full {@link TopicMetadata} (and so reads
 * {@link TopicMetadata#cleanupPolicy()} directly); this cache exists only for {@code join}/{@code
 * joinPassive}, which act on a {@code ReconfigurationCommand} that carries no metadata payload
 * beyond the topic name. A missing entry resolves to {@link CleanupPolicy#DELETE}, matching {@link
 * TopicMetadata}'s own default.
 */
final class TopicCleanupPolicies {

  private final Map<String, CleanupPolicy> policies = new ConcurrentHashMap<>();

  CleanupPolicy get(final String topic) {
    return policies.getOrDefault(topic, CleanupPolicy.DELETE);
  }

  /**
   * Refreshes the cache from an observed registry snapshot, mirroring {@link
   * TopicPartitionCounts#update}.
   */
  void update(final Map<String, TopicMetadata> desired) {
    policies.keySet().removeIf(topic -> !desired.containsKey(topic));
    desired.forEach((name, metadata) -> policies.put(name, metadata.cleanupPolicy()));
  }
}
