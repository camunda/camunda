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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.jspecify.annotations.NullMarked;

/**
 * Answers the client of an answered probe with the worker's answer, data included. Only the engine
 * writes it, right after {@code ANSWER}, which answers the worker: a command carries one response
 * (D74). It answers the client of a probe canceled on its secrets too, as the poll or the failed
 * resolution that cancels it has its own requester or cancels several probes (D75).
 */
@NullMarked
@ExcludeAuthorizationCheck
public final class ProbeRespondProcessor implements TypedRecordProcessor<ProbeRecord> {

  private static final String NOT_INTERNAL_MESSAGE =
      "Expected RESPOND to be written by the engine, but it was sent by a client.";

  private final ProbeBehavior probeBehavior;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public ProbeRespondProcessor(final ProbeBehavior probeBehavior, final Writers writers) {
    this.probeBehavior = probeBehavior;
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<ProbeRecord> command) {
    if (!command.isInternalCommand()) {
      rejectionWriter.appendRejection(
          command, RejectionType.INVALID_ARGUMENT, NOT_INTERNAL_MESSAGE);
      responseWriter.writeRejectedResponseOnCommand(
          command, RejectionType.INVALID_ARGUMENT, NOT_INTERNAL_MESSAGE);
      return;
    }
    final var response = command.getValue();
    probeBehavior.respondToClient(command.getKey(), responseIntentOf(response), response);
  }

  private static ProbeIntent responseIntentOf(final ProbeRecord response) {
    return response.getEndReason() == ProbeEndReason.SECRET_RESOLUTION_ERROR
        ? ProbeIntent.CANCELED
        : ProbeIntent.ANSWERED;
  }
}
