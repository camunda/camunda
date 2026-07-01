/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clusterversion;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.clusterversion.ClusterVersionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ClusterVersionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

/**
 * Processes a {@code SUPPRESS_FLAG} command by emitting a {@code FLAG_SUPPRESSED} event. The
 * suppression is the deck's "rollback-lite" mechanism — an operator can disable a specific behavior
 * flag after it has been activated, without lowering the cluster's ECV.
 */
public final class SuppressFlagProcessor implements TypedRecordProcessor<ClusterVersionRecord> {

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public SuppressFlagProcessor(final Writers writers, final KeyGenerator keyGenerator) {
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<ClusterVersionRecord> command) {
    final var record = command.getValue();
    if (record.getFlagName().isEmpty()) {
      final var msg = "Expected SUPPRESS_FLAG to carry a non-empty flagName, but it was empty";
      rejectionWriter.appendRejection(command, RejectionType.INVALID_ARGUMENT, msg);
      responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_ARGUMENT, msg);
      return;
    }
    final long eventKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(eventKey, ClusterVersionIntent.FLAG_SUPPRESSED, record);
    responseWriter.writeEventOnCommand(
        eventKey, ClusterVersionIntent.FLAG_SUPPRESSED, record, command);
  }
}
