/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.processing;

import io.camunda.eventbridge.clustermetadata.record.BrokerRecord;
import io.camunda.eventbridge.clustermetadata.state.broker.BrokerMetadata.BrokerStatus;
import io.camunda.eventbridge.clustermetadata.state.immutable.BrokerState;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.protocol.record.intent.MetadataIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the internal {@code DRAIN_BROKER} command (from a {@code draining} heartbeat): marks an
 * {@code ACTIVE} broker {@code DRAINING} for controlled shutdown, so placement stops targeting it
 * and the change-coordinator moves its replicas off. Self-guarded against a stale epoch, mirroring
 * {@code FenceBrokerProcessor}. Holds no client response.
 */
public final class DrainBrokerProcessor implements TypedRecordProcessor<BrokerRecord> {

  private final Writers writers;
  private final BrokerState brokerState;

  public DrainBrokerProcessor(final Writers writers, final BrokerState brokerState) {
    this.writers = writers;
    this.brokerState = brokerState;
  }

  @Override
  public void processRecord(final TypedRecord<BrokerRecord> command) {
    final var cmd = command.getValue();
    final var existing = brokerState.get(cmd.getBrokerId());
    if (existing == null
        || existing.status() != BrokerStatus.ACTIVE
        || existing.brokerEpoch() != cmd.getBrokerEpoch()) {
      return;
    }
    final var event =
        new BrokerRecord()
            .setBrokerId(existing.brokerId())
            .setBrokerEpoch(existing.brokerEpoch())
            .setStatus(BrokerStatus.DRAINING)
            .setIncarnation(existing.incarnation());
    writers.state().appendFollowUpEvent(command.getKey(), MetadataIntent.BROKER_DRAINING, event);
  }
}
