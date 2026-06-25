/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.state.mutable;

import io.camunda.eventbridge.clustermetadata.state.broker.BrokerMetadata;
import io.camunda.eventbridge.clustermetadata.state.immutable.BrokerState;

/**
 * Write view of the replicated broker registry — the broker counterpart of {@link
 * MutableTopicState}. Only the appliers use it; granular put/delete write straight to durable
 * state.
 */
public interface MutableBrokerState extends BrokerState {

  /** Inserts or replaces a broker's registration. */
  void put(int brokerId, BrokerMetadata metadata);

  /** Removes a broker. */
  void delete(int brokerId);
}
