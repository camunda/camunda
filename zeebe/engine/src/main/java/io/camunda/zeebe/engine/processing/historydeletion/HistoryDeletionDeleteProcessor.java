/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.historydeletion;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.history.HistoryDeletionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;

// TODO add authorization checks with https://github.com/camunda/camunda/issues/41771
@ExcludeAuthorizationCheck
public class HistoryDeletionDeleteProcessor implements TypedRecordProcessor<HistoryDeletionRecord> {

  private static final String ERROR_MESSAGE_PROCESS_INSTANCE_EXISTS =
      "Expected to delete history for process instance with key '%d', but it is still active.";

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ElementInstanceState elementInstanceState;

  public HistoryDeletionDeleteProcessor(
      final ProcessingState processingState, final Writers writers) {
    elementInstanceState = processingState.getElementInstanceState();
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(final TypedRecord<HistoryDeletionRecord> command) {
    switch (command.getValue().getResourceType()) {
      case PROCESS_INSTANCE -> deleteProcessInstance(command);
      case PROCESS_DEFINITION -> deleteProcessDefinition(command);
      default ->
          throw new UnsupportedOperationException(
              "Unsupported resource type: " + command.getValue().getResourceType());
    }
  }

  private void deleteProcessInstance(final TypedRecord<HistoryDeletionRecord> command) {
    final var recordValue = command.getValue();

    validateProcessInstanceDoesNotExist(recordValue)
        .ifRightOrLeft(
            validRecord -> {
              stateWriter.appendFollowUpEvent(
                  recordValue.getResourceKey(), HistoryDeletionIntent.DELETED, recordValue);
              responseWriter.writeEventOnCommand(
                  recordValue.getResourceKey(),
                  HistoryDeletionIntent.DELETED,
                  recordValue,
                  command);
            },
            rejection -> {
              rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
              responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
            });
  }

  private void deleteProcessDefinition(final TypedRecord<HistoryDeletionRecord> command) {
    final var recordValue = command.getValue();
    stateWriter.appendFollowUpEvent(
        recordValue.getResourceKey(), HistoryDeletionIntent.DELETED, recordValue);
    responseWriter.writeEventOnCommand(
        recordValue.getResourceKey(), HistoryDeletionIntent.DELETED, recordValue, command);
  }

  private Either<Rejection, HistoryDeletionRecord> validateProcessInstanceDoesNotExist(
      final HistoryDeletionRecord record) {
    final var processInstanceKey = record.getResourceKey();
    if (elementInstanceState.getInstance(processInstanceKey) != null) {
      final var rejection =
          new Rejection(
              RejectionType.INVALID_STATE,
              String.format(ERROR_MESSAGE_PROCESS_INSTANCE_EXISTS, processInstanceKey));
      return Either.left(rejection);
    }
    return Either.right(record);
  }
}
