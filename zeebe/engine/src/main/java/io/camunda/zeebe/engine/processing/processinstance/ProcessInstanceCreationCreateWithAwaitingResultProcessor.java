/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.metrics.ProcessEngineMetrics;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.common.EventSubscriptionException;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.instance.AwaitProcessInstanceResultMetadata;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;

@ExcludeAuthorizationCheck
public final class ProcessInstanceCreationCreateWithAwaitingResultProcessor
    implements TypedRecordProcessor<ProcessInstanceCreationRecord> {
  private final KeyGenerator keyGenerator;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final StateWriter stateWriter;
  private final ProcessEngineMetrics metrics;
  private final ProcessInstanceCreationHelper helper;
  private final MutableElementInstanceState elementInstanceState;

  public ProcessInstanceCreationCreateWithAwaitingResultProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final ProcessEngineMetrics metrics,
      final ProcessInstanceCreationHelper processInstanceCreationHelper,
      final MutableElementInstanceState elementInstanceState) {
    this.keyGenerator = keyGenerator;
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    stateWriter = writers.state();
    this.metrics = metrics;
    helper = processInstanceCreationHelper;
    this.elementInstanceState = elementInstanceState;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceCreationRecord> command) {
    final ProcessInstanceCreationRecord record = command.getValue();

    final Either<Rejection, DeployedProcess> persistedProcess = helper.findRelevantProcess(record);
    persistedProcess
        .flatMap(process -> helper.isAuthorized(command, process))
        .flatMap(process -> helper.validateCommand(command.getValue(), process))
        .ifRightOrLeft(
            process -> createProcessInstance(command, process),
            rejection -> reject(command, rejection.type(), rejection.reason()));
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<ProcessInstanceCreationRecord> typedCommand, final Throwable error) {
    if (error instanceof final EventSubscriptionException exception) {
      // This exception is only thrown for ProcessInstanceCreationRecord with start instructions
      rejectionWriter.appendRejection(
          typedCommand, RejectionType.INVALID_ARGUMENT, exception.getMessage());
      responseWriter.writeRejectionOnCommand(
          typedCommand, RejectionType.INVALID_ARGUMENT, exception.getMessage());
      return ProcessingError.EXPECTED_ERROR;
    }
    return ProcessingError.UNEXPECTED_ERROR;
  }

  private void reject(
      final TypedRecord<ProcessInstanceCreationRecord> command,
      final RejectionType type,
      final String reason) {
    rejectionWriter.appendRejection(command, type, reason);
    if (command.hasRequestMetadata()) {
      responseWriter.writeRejectionOnCommand(command, type, reason);
    }
  }

  private void createProcessInstance(
      final TypedRecord<ProcessInstanceCreationRecord> command, final DeployedProcess process) {

    final long processInstanceKey = keyGenerator.nextKey();
    final var commandKey = command.getKey();
    final var record = command.getValue();

    final var processInstance =
        helper.initProcessInstanceRecord(process, processInstanceKey, record.getTags());

    helper.setVariablesFromDocument(processInstance, record.getVariablesBuffer());

    if (record.startInstructions().isEmpty()) {
      commandWriter.appendFollowUpCommand(
          processInstanceKey, ProcessInstanceIntent.ACTIVATE_ELEMENT, processInstance);
    } else {
      helper.activateElementsForStartInstructions(
          record.startInstructions(), process, processInstance);
    }

    helper.updateCreationRecord(record, processInstance);

    final var entityKey = commandKey < 0 ? keyGenerator.nextKey() : commandKey;

    final var awaitResultMetadata =
        new AwaitProcessInstanceResultMetadata()
            .setRequestId(command.getRequestId())
            .setRequestStreamId(command.getRequestStreamId())
            .setFetchVariables(record.fetchVariables());
    elementInstanceState.setAwaitResultRequestMetadata(
        record.getProcessInstanceKey(), awaitResultMetadata);

    stateWriter.appendFollowUpEvent(entityKey, ProcessInstanceCreationIntent.CREATED, record);
    metrics.processInstanceCreated(record);
  }
}
