/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.state.mutable;

import io.camunda.eventbridge.clustermetadata.state.immutable.TopicState;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata;

/**
 * Write view of the replicated topic registry — the event-bridge counterpart of the engine's {@code
 * MutableXxxState}. Only the appliers use it; it exposes granular put/delete primitives that write
 * straight to the durable state.
 */
public interface MutableTopicState extends TopicState {

  /** Inserts or replaces a topic's desired configuration. */
  void put(String name, TopicMetadata metadata);

  /** Removes a topic. */
  void delete(String name);
}
