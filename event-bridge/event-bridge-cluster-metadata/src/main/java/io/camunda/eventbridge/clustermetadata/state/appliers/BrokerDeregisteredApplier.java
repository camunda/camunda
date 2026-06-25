/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.state.appliers;

import io.camunda.eventbridge.clustermetadata.record.BrokerRecord;
import io.camunda.eventbridge.clustermetadata.state.mutable.MutableBrokerState;
import io.camunda.eventbridge.stream.TypedEventApplier;
import io.camunda.zeebe.protocol.record.intent.MetadataIntent;

/**
 * Applies {@code BROKER_DEREGISTERED} events: removes the broker from the registry once it has
 * drained / shut down. Runs identically on leader and follower/observer.
 */
public final class BrokerDeregisteredApplier
    implements TypedEventApplier<MetadataIntent, BrokerRecord> {

  private final MutableBrokerState brokerState;

  public BrokerDeregisteredApplier(final MutableBrokerState brokerState) {
    this.brokerState = brokerState;
  }

  @Override
  public void applyState(final long key, final BrokerRecord value) {
    brokerState.delete(value.getBrokerId());
  }
}
