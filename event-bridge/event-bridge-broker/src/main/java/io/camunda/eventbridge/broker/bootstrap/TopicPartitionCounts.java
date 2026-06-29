/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.bootstrap;

import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata.TopicStatus;
import io.camunda.eventbridge.consumergroups.membership.TopicRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread-safe topic&rarr;partitionCount cache fed by observed metadata-registry snapshots and
 * read as a {@link TopicRegistry} by the coordinator leader (which observes the metadata group on
 * this broker too) to resolve a group's partition count from its subscribed topic, rather than from
 * a static config.
 *
 * <p>Only servable topics (anything but {@code DELETING}) are cached; a missing entry resolves to
 * {@code 0}, which the join processor rejects as an unknown topic.
 */
final class TopicPartitionCounts implements TopicRegistry {

  private final Map<String, Integer> counts = new ConcurrentHashMap<>();

  @Override
  public int partitionCount(final String topic) {
    return counts.getOrDefault(topic, 0);
  }

  /**
   * Refreshes the cache from an observed registry snapshot: servable topics keep their count,
   * removed/deleting topics drop out.
   */
  void update(final Map<String, TopicMetadata> desired) {
    counts.keySet().removeIf(topic -> !desired.containsKey(topic));
    desired.forEach(
        (name, metadata) -> {
          if (metadata.status() == TopicStatus.DELETING) {
            counts.remove(name);
          } else {
            counts.put(name, metadata.partitionCount());
          }
        });
  }
}
