/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.metrics.ProcessEngineMetrics;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.common.ElementActivationBehavior;
import io.camunda.zeebe.engine.processing.common.EventSubscriptionException;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.util.Set;
import org.agrona.DirectBuffer;

public final class ProcessInstanceCreationCreateProcessor
    implements TypedRecordProcessor<ProcessInstanceCreationRecord> {

  private final ProcessInstanceRecord newProcessInstance = new ProcessInstanceRecord();

  private final VariableBehavior variableBehavior;

  private final KeyGenerator keyGenerator;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final StateWriter stateWriter;

  private final ProcessEngineMetrics metrics;

  private final ElementActivationBehavior elementActivationBehavior;
  private final ProcessInstanceCreationHelper processInstanceCreationHelper;

  public ProcessInstanceCreationCreateProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final BpmnBehaviors bpmnBehaviors,
      final ProcessEngineMetrics metrics,
      final ProcessInstanceCreationHelper processInstanceCreationHelper) {
    variableBehavior = bpmnBehaviors.variableBehavior();
    this.keyGenerator = keyGenerator;
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    stateWriter = writers.state();
    this.metrics = metrics;
    elementActivationBehavior = bpmnBehaviors.elementActivationBehavior();
    this.processInstanceCreationHelper = processInstanceCreationHelper;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceCreationRecord> command) {
    final ProcessInstanceCreationRecord record = command.getValue();

    final Either<Rejection, DeployedProcess> persistedProcess =
        processInstanceCreationHelper.findRelevantProcess(record);
    persistedProcess
        .flatMap(process -> processInstanceCreationHelper.isAuthorized(command, process))
        .flatMap(
            process -> processInstanceCreationHelper.validateCommand(command.getValue(), process))
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

  public void reject(
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

    setVariablesFromDocument(
        record,
        process.getKey(),
        processInstanceKey,
        process.getBpmnProcessId(),
        process.getTenantId());

    final var processInstance =
        initProcessInstanceRecord(process, processInstanceKey, record.getTags());

    if (record.startInstructions().isEmpty()) {
      commandWriter.appendFollowUpCommand(
          processInstanceKey, ProcessInstanceIntent.ACTIVATE_ELEMENT, processInstance);
    } else {
      activateElementsForStartInstructions(record.startInstructions(), process, processInstance);
    }

    record
        .setProcessInstanceKey(processInstanceKey)
        .setBpmnProcessId(process.getBpmnProcessId())
        .setVersion(process.getVersion())
        .setProcessDefinitionKey(process.getKey());

    final var entityKey = commandKey < 0 ? keyGenerator.nextKey() : commandKey;

    stateWriter.appendFollowUpEvent(entityKey, ProcessInstanceCreationIntent.CREATED, record);
    if (command.hasRequestMetadata()) {
      responseWriter.writeEventOnCommand(
          entityKey, ProcessInstanceCreationIntent.CREATED, record, command);
    }

    metrics.processInstanceCreated(record);
  }

  private void setVariablesFromDocument(
      final ProcessInstanceCreationRecord record,
      final long processDefinitionKey,
      final long processInstanceKey,
      final DirectBuffer bpmnProcessId,
      final String tenantId) {

    variableBehavior.mergeLocalDocument(
        processInstanceKey,
        processDefinitionKey,
        processInstanceKey,
        bpmnProcessId,
        tenantId,
        record.getVariablesBuffer());
  }

  private ProcessInstanceRecord initProcessInstanceRecord(
      final DeployedProcess process, final long processInstanceKey, final Set<String> tags) {
    newProcessInstance.reset();
    newProcessInstance.setBpmnProcessId(process.getBpmnProcessId());
    newProcessInstance.setVersion(process.getVersion());
    newProcessInstance.setProcessDefinitionKey(process.getKey());
    newProcessInstance.setProcessInstanceKey(processInstanceKey);
    newProcessInstance.setBpmnElementType(BpmnElementType.PROCESS);
    newProcessInstance.setElementId(process.getProcess().getId());
    newProcessInstance.setFlowScopeKey(-1);
    newProcessInstance.setTenantId(process.getTenantId());
    newProcessInstance.setTags(tags);
    return newProcessInstance;
  }

  private void activateElementsForStartInstructions(
      final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions,
      final DeployedProcess process,
      final ProcessInstanceRecord processInstance) {

    startInstructions.forEach(
        instruction -> {
          final var element = process.getProcess().getElementById(instruction.getElementId());
          elementActivationBehavior.activateElement(processInstance, element);
        });
  }
}
