/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue.ProcessInstanceMigrationMappingInstructionValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.concurrent.UnsafeBuffer;

public class ProcessInstanceMigrationMigrateProcessor
    implements TypedRecordProcessor<ProcessInstanceMigrationRecord> {

  private static final EnumSet<BpmnElementType> SUPPORTED_ELEMENT_TYPES =
      EnumSet.of(BpmnElementType.PROCESS, BpmnElementType.SERVICE_TASK);
  private static final Set<BpmnElementType> UNSUPPORTED_ELEMENT_TYPES =
      EnumSet.complementOf(SUPPORTED_ELEMENT_TYPES);

  private static final String ERROR_MESSAGE_PROCESS_INSTANCE_NOT_FOUND =
      "Expected to migrate process instance but no process instance found with key '%d'";
  private static final String ERROR_MESSAGE_PROCESS_DEFINITION_NOT_FOUND =
      "Expected to migrate process instance to process definition but no process definition found with key '%d'";

  private static final UnsafeBuffer NIL_VALUE = new UnsafeBuffer(MsgPackHelper.NIL);
  private final VariableRecord variableRecord = new VariableRecord().setValue(NIL_VALUE);

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final JobState jobState;
  private final VariableState variableState;

  public ProcessInstanceMigrationMigrateProcessor(
      final Writers writers,
      final ElementInstanceState elementInstanceState,
      final ProcessState processState,
      final JobState jobState,
      final VariableState variableState) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
    this.jobState = jobState;
    this.variableState = variableState;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceMigrationRecord> command) {
    final ProcessInstanceMigrationRecord value = command.getValue();
    final long processInstanceKey = value.getProcessInstanceKey();
    final long targetProcessDefinitionKey = value.getTargetProcessDefinitionKey();
    final var mappingInstructions = value.getMappingInstructions();

    final ElementInstance processInstance = elementInstanceState.getInstance(processInstanceKey);
    if (processInstance == null) {
      final String reason =
          String.format(ERROR_MESSAGE_PROCESS_INSTANCE_NOT_FOUND, processInstanceKey);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, reason);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, reason);
      return;
    }

    final DeployedProcess processDefinition =
        processState.getProcessByKeyAndTenant(
            targetProcessDefinitionKey, processInstance.getValue().getTenantId());
    if (processDefinition == null) {
      final String reason =
          String.format(ERROR_MESSAGE_PROCESS_DEFINITION_NOT_FOUND, targetProcessDefinitionKey);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, reason);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, reason);
      return;
    }

    final Map<String, String> mappedElementIds =
        mapElementIds(mappingInstructions, processInstance, processDefinition);

    // avoid stackoverflow using a queue to iterate over the descendants instead of recursion
    final var elementInstances = new ArrayDeque<>(List.of(processInstance));
    while (!elementInstances.isEmpty()) {
      final var elementInstance = elementInstances.poll();
      tryMigrateElementInstance(elementInstance, processDefinition, mappedElementIds);
      final List<ElementInstance> children =
          elementInstanceState.getChildren(elementInstance.getKey());
      elementInstances.addAll(children);
    }

    stateWriter.appendFollowUpEvent(
        processInstanceKey, ProcessInstanceMigrationIntent.MIGRATED, value);
    responseWriter.writeEventOnCommand(
        processInstanceKey, ProcessInstanceMigrationIntent.MIGRATED, value, command);
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<ProcessInstanceMigrationRecord> command, final Throwable error) {
    if (error instanceof final UnsupportedElementMigrationException e) {
      final String reason =
          "Expected to migrate process instance '%s' but it contains an active element that is unsupported: %s"
              .formatted(command.getKey(), e.getMessage());
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
      responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_STATE, reason);
      return ProcessingError.EXPECTED_ERROR;
    }

    return ProcessingError.UNEXPECTED_ERROR;
  }

  private Map<String, String> mapElementIds(
      final List<ProcessInstanceMigrationMappingInstructionValue> mappingInstructions,
      final ElementInstance processInstance,
      final DeployedProcess targetProcessDefinition) {
    final Map<String, String> mappedElementIds =
        mappingInstructions.stream()
            .collect(
                Collectors.toMap(
                    ProcessInstanceMigrationMappingInstructionValue::getSourceElementId,
                    ProcessInstanceMigrationMappingInstructionValue::getTargetElementId));
    // users don't provide a mapping instruction for the bpmn process id
    mappedElementIds.put(
        processInstance.getValue().getBpmnProcessId(),
        BufferUtil.bufferAsString(targetProcessDefinition.getBpmnProcessId()));
    return mappedElementIds;
  }

  private void tryMigrateElementInstance(
      final ElementInstance elementInstance,
      final DeployedProcess processDefinition,
      final Map<String, String> sourceElementIdToTargetElementId) {

    final var elementInstanceRecord = elementInstance.getValue();
    if (UNSUPPORTED_ELEMENT_TYPES.contains(elementInstanceRecord.getBpmnElementType())) {
      throw new UnsupportedElementMigrationException(
          elementInstanceRecord.getElementId(), elementInstanceRecord.getBpmnElementType());
    }

    final String targetElementId =
        sourceElementIdToTargetElementId.get(elementInstanceRecord.getElementId());

    stateWriter.appendFollowUpEvent(
        elementInstance.getKey(),
        ProcessInstanceIntent.ELEMENT_MIGRATED,
        elementInstanceRecord
            .setProcessDefinitionKey(processDefinition.getKey())
            .setBpmnProcessId(processDefinition.getBpmnProcessId())
            .setVersion(processDefinition.getVersion())
            .setElementId(targetElementId));

    if (elementInstance.getJobKey() > 0) {
      final var job = jobState.getJob(elementInstance.getJobKey());
      if (job != null) {
        stateWriter.appendFollowUpEvent(
            elementInstance.getJobKey(),
            JobIntent.MIGRATED,
            job.setProcessDefinitionKey(processDefinition.getKey())
                .setProcessDefinitionVersion(processDefinition.getVersion())
                .setBpmnProcessId(processDefinition.getBpmnProcessId())
                .setElementId(targetElementId));
      }
    }

    variableState
        .getVariablesLocal(elementInstance.getKey())
        .forEach(
            variable ->
                stateWriter.appendFollowUpEvent(
                    variable.key(),
                    VariableIntent.MIGRATED,
                    variableRecord
                        .setScopeKey(elementInstance.getKey())
                        .setName(variable.name())
                        .setProcessInstanceKey(elementInstance.getValue().getProcessInstanceKey())
                        .setProcessDefinitionKey(processDefinition.getKey())
                        .setBpmnProcessId(processDefinition.getBpmnProcessId())
                        .setTenantId(elementInstance.getValue().getTenantId())));
  }

  /**
   * Exception that can be thrown during the migration of a process instance, in case the engine
   * attempts to migrate an element which is not supported at this time.
   */
  private static final class UnsupportedElementMigrationException extends RuntimeException {
    UnsupportedElementMigrationException(
        final String elementId, final BpmnElementType bpmnElementType) {
      super("%s. The migration of a %s is not supported.".formatted(elementId, bpmnElementType));
    }
  }
}
