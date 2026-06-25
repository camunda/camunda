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
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MetadataIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the internal {@code DRAIN_BROKER} command (from a {@code draining} heartbeat): marks an
 * {@code ACTIVE} broker {@code DRAINING} for controlled shutdown, so placement stops targeting it
 * and the change-coordinator moves its replicas off. {@link
 * BrokerTransitionValidator#validateDrain} guards the transition; on success it appends {@code
 * BROKER_DRAINING}, otherwise a {@code COMMAND_REJECTION} with the reason (the command carries no
 * request, so there is no reply).
 */
public final class DrainBrokerProcessor implements TypedRecordProcessor<BrokerRecord> {

  private final Writers writers;
  private final BrokerState brokerState;
  private final BrokerTransitionValidator validator;

  public DrainBrokerProcessor(
      final Writers writers,
      final BrokerState brokerState,
      final BrokerTransitionValidator validator) {
    this.writers = writers;
    this.brokerState = brokerState;
    this.validator = validator;
  }

  @Override
  public void processRecord(final TypedRecord<BrokerRecord> command) {
    validator
        .validateDrain(command.getValue())
        .ifRightOrLeft(ok -> drain(command), reason -> reject(command, reason));
  }

  private void drain(final TypedRecord<BrokerRecord> command) {
    final var broker = brokerState.get(command.getValue().getBrokerId());
    final var event =
        new BrokerRecord()
            .setBrokerId(broker.brokerId())
            .setBrokerEpoch(broker.brokerEpoch())
            .setStatus(BrokerStatus.DRAINING)
            .setIncarnation(broker.incarnation());
    writers.state().appendFollowUpEvent(command.getKey(), MetadataIntent.BROKER_DRAINING, event);
  }

  private void reject(final TypedRecord<BrokerRecord> command, final String reason) {
    writers.rejection().appendRejection(command, RejectionType.INVALID_STATE, reason);
  }
}
