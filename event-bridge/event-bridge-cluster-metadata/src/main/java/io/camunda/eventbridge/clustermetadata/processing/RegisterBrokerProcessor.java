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
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.protocol.request.coordination.RegisterBrokerResponse;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.protocol.record.intent.MetadataIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the {@code REGISTER_BROKER} command: assigns the broker a fresh, monotonic epoch (one
 * higher than any prior registration of the same id, so a restarted broker fences its earlier
 * incarnation), appends {@code BROKER_REGISTERED} with status {@code ACTIVE}, and replies with the
 * assigned epoch. The leader that produces the durable event decides the epoch.
 */
public final class RegisterBrokerProcessor implements TypedRecordProcessor<BrokerRecord> {

  private final Writers writers;
  private final BrokerState brokerState;

  public RegisterBrokerProcessor(final Writers writers, final BrokerState brokerState) {
    this.writers = writers;
    this.brokerState = brokerState;
  }

  @Override
  public void processRecord(final TypedRecord<BrokerRecord> command) {
    final var cmd = command.getValue();
    final var existing = brokerState.get(cmd.getBrokerId());
    final var epoch = (existing == null ? 0L : existing.brokerEpoch()) + 1;
    final var event =
        new BrokerRecord()
            .setBrokerId(cmd.getBrokerId())
            .setBrokerEpoch(epoch)
            .setStatus(BrokerStatus.ACTIVE)
            .setIncarnation(cmd.getIncarnation());
    writers.state().appendFollowUpEvent(command.getKey(), MetadataIntent.BROKER_REGISTERED, event);
    writers
        .response()
        .respond(
            command,
            new RegisterBrokerResponse()
                .setErrorCode(CoordinationErrorCode.NONE)
                .setBrokerEpoch(epoch));
  }
}
