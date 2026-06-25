/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.state.immutable;

import io.camunda.eventbridge.clustermetadata.state.broker.BrokerMetadata;
import java.util.List;
import java.util.Map;

/**
 * Read view of the replicated broker registry — the broker counterpart of {@link TopicState}.
 * Processors/validators and the liveness handler depend on this (never on the concrete {@code Db…}
 * class); off-actor readers reach it through their own {@code BrokerQueryService} (a private
 * context, no shared flyweights).
 */
public interface BrokerState {

  /** The broker's registration, or {@code null} if it is not registered. */
  BrokerMetadata get(int brokerId);

  /** A pinned snapshot of all registered brokers ({@code brokerId → metadata}). */
  Map<Integer, BrokerMetadata> brokersSnapshot();

  /**
   * The ids of brokers eligible for placement — those in {@link
   * BrokerMetadata.BrokerStatus#ACTIVE}, ascending. Fenced/draining brokers are excluded.
   */
  List<Integer> activeBrokers();
}
