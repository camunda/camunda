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
 * Applies {@code BROKER_REGISTERED} events: writes the broker's registration (epoch + {@code
 * ACTIVE} status) verbatim. Runs identically on leader (after {@code RegisterBrokerProcessor}) and
 * follower/observer (on replay).
 */
public final class BrokerRegisteredApplier
    implements TypedEventApplier<MetadataIntent, BrokerRecord> {

  private final MutableBrokerState brokerState;

  public BrokerRegisteredApplier(final MutableBrokerState brokerState) {
    this.brokerState = brokerState;
  }

  @Override
  public void applyState(final long key, final BrokerRecord value) {
    brokerState.put(value.getBrokerId(), value.toMetadata());
  }
}
