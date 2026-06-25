/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.processing;

import io.camunda.eventbridge.clustermetadata.record.BrokerRecord;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MetadataIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the internal {@code DEREGISTER_BROKER} command: removes a broker from the registry once
 * it has drained and shut down. {@link BrokerTransitionValidator#validateDeregister} guards that
 * the broker still exists; on success it appends {@code BROKER_DEREGISTERED}, otherwise a {@code
 * COMMAND_REJECTION} with the reason (the command carries no request, so there is no reply).
 */
public final class DeregisterBrokerProcessor implements TypedRecordProcessor<BrokerRecord> {

  private final Writers writers;
  private final BrokerTransitionValidator validator;

  public DeregisterBrokerProcessor(
      final Writers writers, final BrokerTransitionValidator validator) {
    this.writers = writers;
    this.validator = validator;
  }

  @Override
  public void processRecord(final TypedRecord<BrokerRecord> command) {
    validator
        .validateDeregister(command.getValue())
        .ifRightOrLeft(ok -> deregister(command), reason -> reject(command, reason));
  }

  private void deregister(final TypedRecord<BrokerRecord> command) {
    writers
        .state()
        .appendFollowUpEvent(
            command.getKey(),
            MetadataIntent.BROKER_DEREGISTERED,
            new BrokerRecord().setBrokerId(command.getValue().getBrokerId()));
  }

  private void reject(final TypedRecord<BrokerRecord> command, final String reason) {
    writers.rejection().appendRejection(command, RejectionType.INVALID_STATE, reason);
  }
}
