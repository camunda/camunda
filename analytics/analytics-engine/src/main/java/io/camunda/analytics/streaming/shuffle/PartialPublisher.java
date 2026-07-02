/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.shuffle;

/**
 * Publishes {@link Partial}s to the facts topic (the shuffle). The library defines the contract;
 * the event-bridge module supplies the implementation (routing each partial to a facts partition by
 * {@code hash(aggId, key)} and awaiting durability). Kept in the streaming library so the combiner
 * sink has no transport dependency.
 */
public interface PartialPublisher {

  /** Buffers a partial for publication (routed by {@code hash(aggId, key)}). */
  void publish(Partial partial);

  /** Publishes everything buffered and blocks until it is durable (produce-before-checkpoint). */
  void flush();
}
