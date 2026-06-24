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
 * Applies {@code TOPIC_DELETED} events to the replicated registry — the only place that removes a
 * topic from {@link DbTopicState}. It also evicts the entry from the in-memory {@code
 * registryCache} (owned by {@link MetadataStream}) in lockstep with the durable state.
 */
final class TopicDeletedApplier implements TypedEventApplier<MetadataIntent, TopicRecord> {

  private final DbTopicState topicState;
  private final Map<String, TopicMetadata> registryCache;

  TopicDeletedApplier(
      final DbTopicState topicState, final Map<String, TopicMetadata> registryCache) {
    this.topicState = topicState;
    this.registryCache = registryCache;
  }

  @Override
  public void applyState(final long key, final TopicRecord value) {
    topicState.delete(value.getName());
    registryCache.remove(value.getName());
  }
}
