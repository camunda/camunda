/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.stream;

import io.camunda.eventbridge.stream.TypedEventApplier;
import java.util.Map;

/**
 * Applies {@code TOPIC_REGISTERED} events to the replicated registry — the only place that adds a
 * topic to {@link DbTopicState}. It also updates the in-memory {@code registryCache} (owned by
 * {@link MetadataStream}) in lockstep with the durable state; this runs on the stream's actor, so
 * reads from other actors go through the thread-safe cache rather than the stream-owned state DB.
 */
final class TopicRegisteredApplier implements TypedEventApplier<MetadataIntent, TopicRecord> {

  private final DbTopicState topicState;
  private final Map<String, TopicMetadata> registryCache;

  TopicRegisteredApplier(
      final DbTopicState topicState, final Map<String, TopicMetadata> registryCache) {
    this.topicState = topicState;
    this.registryCache = registryCache;
  }

  @Override
  public void applyState(final long key, final TopicRecord value) {
    final var metadata = value.toMetadata();
    topicState.put(value.getName(), metadata);
    registryCache.put(value.getName(), metadata);
  }
}
