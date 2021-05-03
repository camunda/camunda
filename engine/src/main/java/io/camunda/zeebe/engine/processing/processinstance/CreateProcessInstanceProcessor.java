/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.processinstance;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.processing.variable.VariableBehavior;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.deployment.DeployedProcess;
import io.zeebe.engine.state.immutable.ProcessState;
import io.zeebe.msgpack.spec.MsgpackReaderException;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import org.agrona.DirectBuffer;

public final class CreateProcessInstanceProcessor
    implements CommandProcessor<ProcessInstanceCreationRecord> {

  private static final String ERROR_MESSAGE_NO_IDENTIFIER_SPECIFIED =
      "Expected at least a bpmnProcessId or a key greater than -1, but none given";
  private static final String ERROR_MESSAGE_NOT_FOUND_BY_PROCESS =
      "Expected to find process definition with process ID '%s', but none found";
  private static final String ERROR_MESSAGE_NOT_FOUND_BY_PROCESS_AND_VERSION =
      "Expected to find process definition with process ID '%s' and version '%d', but none found";
  private static final String ERROR_MESSAGE_NOT_FOUND_BY_KEY =
      "Expected to find process definition with key '%d', but none found";
  private static final String ERROR_MESSAGE_NO_NONE_START_EVENT =
      "Expected to create instance of process with none start event, but there is no such event";
  private static final String ERROR_INVALID_VARIABLES_REJECTION_MESSAGE =
      "Expected to set variables from document, but the document is invalid: '%s'";
  private static final String ERROR_INVALID_VARIABLES_LOGGED_MESSAGE =
      "Expected to set variables from document, but the document is invalid";

  private final ProcessInstanceRecord newProcessInstance = new ProcessInstanceRecord();
  private final ProcessState processState;
  private final VariableBehavior variableBehavior;
  private final KeyGenerator keyGenerator;
  private final TypedCommandWriter commandWriter;

  public CreateProcessInstanceProcessor(
      final ProcessState processState,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final VariableBehavior variableBehavior) {
    this.processState = processState;
    this.variableBehavior = variableBehavior;
    this.keyGenerator = keyGenerator;
    commandWriter = writers.command();
  }

  @Override
  public boolean onCommand(
      final TypedRecord<ProcessInstanceCreationRecord> command,
      final CommandControl<ProcessInstanceCreationRecord> controller) {
    final ProcessInstanceCreationRecord record = command.getValue();
    final DeployedProcess process = getProcess(record, controller);
    if (process == null || !isValidProcess(controller, process)) {
      return true;
    }

    final long processInstanceKey = keyGenerator.nextKey();
    if (!setVariablesFromDocument(controller, record, process.getKey(), processInstanceKey)) {
      return true;
    }

    final var processInstance = initProcessInstanceRecord(process, processInstanceKey);
    commandWriter.appendFollowUpCommand(
        processInstanceKey, ProcessInstanceIntent.ACTIVATE_ELEMENT, processInstance);

    record
        .setProcessInstanceKey(processInstanceKey)
        .setBpmnProcessId(process.getBpmnProcessId())
        .setVersion(process.getVersion())
        .setProcessDefinitionKey(process.getKey());
    controller.accept(ProcessInstanceCreationIntent.CREATED, record);
    return true;
  }

  private boolean isValidProcess(
      final CommandControl<ProcessInstanceCreationRecord> controller,
      final DeployedProcess process) {
    if (process.getProcess().getNoneStartEvent() == null) {
      controller.reject(RejectionType.INVALID_STATE, ERROR_MESSAGE_NO_NONE_START_EVENT);
      return false;
    }

    return true;
  }

  private boolean setVariablesFromDocument(
      final CommandControl<ProcessInstanceCreationRecord> controller,
      final ProcessInstanceCreationRecord record,
      final long processDefinitionKey,
      final long processInstanceKey) {
    try {
      variableBehavior.mergeLocalDocument(
          processInstanceKey,
          processDefinitionKey,
          processInstanceKey,
          record.getVariablesBuffer());
    } catch (final MsgpackReaderException e) {
      Loggers.PROCESS_PROCESSOR_LOGGER.error(ERROR_INVALID_VARIABLES_LOGGED_MESSAGE, e);
      controller.reject(
          RejectionType.INVALID_ARGUMENT,
          String.format(ERROR_INVALID_VARIABLES_REJECTION_MESSAGE, e.getMessage()));

      return false;
    }

    return true;
  }

  private ProcessInstanceRecord initProcessInstanceRecord(
      final DeployedProcess process, final long processInstanceKey) {
    newProcessInstance.reset();
    newProcessInstance.setBpmnProcessId(process.getBpmnProcessId());
    newProcessInstance.setVersion(process.getVersion());
    newProcessInstance.setProcessDefinitionKey(process.getKey());
    newProcessInstance.setProcessInstanceKey(processInstanceKey);
    newProcessInstance.setBpmnElementType(BpmnElementType.PROCESS);
    newProcessInstance.setElementId(process.getProcess().getId());
    newProcessInstance.setFlowScopeKey(-1);
    return newProcessInstance;
  }

  private DeployedProcess getProcess(
      final ProcessInstanceCreationRecord record, final CommandControl controller) {
    final DeployedProcess process;

    final DirectBuffer bpmnProcessId = record.getBpmnProcessIdBuffer();

    if (bpmnProcessId.capacity() > 0) {
      if (record.getVersion() >= 0) {
        process = getProcess(bpmnProcessId, record.getVersion(), controller);
      } else {
        process = getProcess(bpmnProcessId, controller);
      }
    } else if (record.getProcessDefinitionKey() >= 0) {
      process = getProcess(record.getProcessDefinitionKey(), controller);
    } else {
      controller.reject(RejectionType.INVALID_ARGUMENT, ERROR_MESSAGE_NO_IDENTIFIER_SPECIFIED);
      process = null;
    }

    return process;
  }

  private DeployedProcess getProcess(
      final DirectBuffer bpmnProcessId, final CommandControl controller) {
    final DeployedProcess process = processState.getLatestProcessVersionByProcessId(bpmnProcessId);
    if (process == null) {
      controller.reject(
          RejectionType.NOT_FOUND,
          String.format(ERROR_MESSAGE_NOT_FOUND_BY_PROCESS, bufferAsString(bpmnProcessId)));
    }

    return process;
  }

  private DeployedProcess getProcess(
      final DirectBuffer bpmnProcessId, final int version, final CommandControl controller) {
    final DeployedProcess process =
        processState.getProcessByProcessIdAndVersion(bpmnProcessId, version);
    if (process == null) {
      controller.reject(
          RejectionType.NOT_FOUND,
          String.format(
              ERROR_MESSAGE_NOT_FOUND_BY_PROCESS_AND_VERSION,
              bufferAsString(bpmnProcessId),
              version));
    }

    return process;
  }

  private DeployedProcess getProcess(final long key, final CommandControl controller) {
    final DeployedProcess process = processState.getProcessByKey(key);
    if (process == null) {
      controller.reject(
          RejectionType.NOT_FOUND, String.format(ERROR_MESSAGE_NOT_FOUND_BY_KEY, key));
    }

    return process;
  }
}
