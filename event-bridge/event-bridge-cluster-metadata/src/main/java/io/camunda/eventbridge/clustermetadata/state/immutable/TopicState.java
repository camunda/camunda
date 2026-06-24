/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.state.immutable;

import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata;

import java.util.Map;

/**
 * Read view of the replicated topic registry — the event-bridge counterpart of the engine's
 * immutable {@code XxxState} interfaces. Processors/validators depend on this (never on the concrete
 * {@code Db…} class); the metadata leader and each broker's reconcile read the thread-safe mirror
 * ({@link #topicsSnapshot}) off the stream-processing actor.
 */
public interface TopicState {

  /** The topic's desired configuration, or {@code null} if the topic does not exist. */
  TopicMetadata get(String name);

  /** A thread-safe snapshot of all registered topics ({@code topicName → metadata}). */
  Map<String, TopicMetadata> topicsSnapshot();
}
