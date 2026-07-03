/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Suspends a (root) process instance (POC #56552): forward-progress BPMN element commands are
 * diverted into a buffer by the gate in {@code BpmnStreamProcessor} until the instance is resumed.
 */
public final class ProcessInstanceSuspendProcessor
    implements TypedRecordProcessor<ProcessInstanceRecord> {

  private static final String MESSAGE_PREFIX =
      "Expected to suspend a process instance with key '%d', but ";
  private static final String PROCESS_NOT_FOUND_MESSAGE =
      MESSAGE_PREFIX + "no such process was found";
  private static final String NOT_ROOT_MESSAGE =
      MESSAGE_PREFIX
          + "it is created by a parent process instance. Suspend the root process instance '%d' instead.";
  private static final String ALREADY_SUSPENDED_MESSAGE =
      MESSAGE_PREFIX + "it is already suspended";

  private final ElementInstanceState elementInstanceState;
  private final SuspensionState suspensionState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public ProcessInstanceSuspendProcessor(
      final ElementInstanceState elementInstanceState,
      final SuspensionState suspensionState,
      final Writers writers) {
    this.elementInstanceState = elementInstanceState;
    this.suspensionState = suspensionState;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceRecord> command) {
    final var elementInstance = elementInstanceState.getInstance(command.getKey());

    if (elementInstance == null || elementInstance.getParentKey() > 0) {
      reject(command, RejectionType.NOT_FOUND, PROCESS_NOT_FOUND_MESSAGE);
      return;
    }

    final var value = elementInstance.getValue();
    if (value.getParentProcessInstanceKey() > 0) {
      reject(
          command,
          RejectionType.INVALID_STATE,
          String.format(NOT_ROOT_MESSAGE, command.getKey(), value.getParentProcessInstanceKey()));
      return;
    }

    if (suspensionState.isSuspended(command.getKey())) {
      reject(command, RejectionType.INVALID_STATE, ALREADY_SUSPENDED_MESSAGE);
      return;
    }

    stateWriter.appendFollowUpEvent(command.getKey(), ProcessInstanceIntent.SUSPENDED, value);
    responseWriter.writeEventOnCommand(
        command.getKey(), ProcessInstanceIntent.SUSPENDED, value, command);
  }

  private void reject(
      final TypedRecord<ProcessInstanceRecord> command,
      final RejectionType type,
      final String message) {
    final String formatted =
        message.contains("%d") ? String.format(message, command.getKey()) : message;
    rejectionWriter.appendRejection(command, type, formatted);
    responseWriter.writeRejectionOnCommand(command, type, formatted);
  }
}
