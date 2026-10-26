/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Answers the client waiting for a probe with the worker's answer, which {@link
 * ProbeAnswerProcessor} already recorded. Writes no probe event; only the engine writes this
 * command.
 */
@ExcludeAuthorizationCheck
public final class ProbeRespondProcessor implements TypedRecordProcessor<ProbeRecord> {

  private final ProbeBehavior probeBehavior;
  private final TypedRejectionWriter rejectionWriter;

  public ProbeRespondProcessor(final ProbeBehavior probeBehavior, final Writers writers) {
    this.probeBehavior = probeBehavior;
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(final TypedRecord<ProbeRecord> command) {
    if (!command.isInternalCommand()) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_ARGUMENT,
          "Expected RESPOND to be written by the engine, but it was sent by a client.");
      return;
    }
    probeBehavior.respondToClient(command.getKey(), ProbeIntent.ANSWERED, command.getValue());
  }
}
