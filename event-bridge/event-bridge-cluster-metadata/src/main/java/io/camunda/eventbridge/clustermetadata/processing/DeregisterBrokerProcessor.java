/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.processing;

import io.camunda.eventbridge.clustermetadata.record.BrokerRecord;
import io.camunda.eventbridge.clustermetadata.state.immutable.BrokerState;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.protocol.record.intent.MetadataIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the internal {@code DEREGISTER_BROKER} command: removes a broker from the registry once
 * it has drained and shut down. A no-op if the broker is already gone. Holds no client response.
 */
public final class DeregisterBrokerProcessor implements TypedRecordProcessor<BrokerRecord> {

  private final Writers writers;
  private final BrokerState brokerState;

  public DeregisterBrokerProcessor(final Writers writers, final BrokerState brokerState) {
    this.writers = writers;
    this.brokerState = brokerState;
  }

  @Override
  public void processRecord(final TypedRecord<BrokerRecord> command) {
    final var cmd = command.getValue();
    if (brokerState.get(cmd.getBrokerId()) == null) {
      return;
    }
    writers
        .state()
        .appendFollowUpEvent(
            command.getKey(),
            MetadataIntent.BROKER_DEREGISTERED,
            new BrokerRecord().setBrokerId(cmd.getBrokerId()));
  }
}
