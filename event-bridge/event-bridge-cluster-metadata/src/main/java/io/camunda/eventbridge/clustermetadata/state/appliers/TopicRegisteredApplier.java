/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.state.appliers;

import io.camunda.eventbridge.clustermetadata.record.TopicRecord;
import io.camunda.eventbridge.clustermetadata.state.mutable.MutableTopicState;
import io.camunda.eventbridge.stream.TypedEventApplier;
import io.camunda.zeebe.protocol.record.intent.MetadataIntent;

/**
 * Applies {@code TOPIC_REGISTERED} events to the replicated registry — the only place that adds (or
 * updates) a topic in {@link MutableTopicState}. Runs identically on leader (after the topic
 * processors) and follower/observer (on replay), so every replica converges.
 */
public final class TopicRegisteredApplier
    implements TypedEventApplier<MetadataIntent, TopicRecord> {

  private final MutableTopicState topicState;

  public TopicRegisteredApplier(final MutableTopicState topicState) {
    this.topicState = topicState;
  }

  @Override
  public void applyState(final long key, final TopicRecord value) {
    topicState.put(value.getName(), value.toMetadata());
  }
}
