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
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue.ProcessInstanceMigrationMappingInstructionValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProcessInstanceMigrationMigrateProcessor
    implements TypedRecordProcessor<ProcessInstanceMigrationRecord> {

  private static final String ERROR_MESSAGE_PROCESS_INSTANCE_NOT_FOUND =
      "Expected to migrate process instance but no process instance found with key '%d'";
  private static final String ERROR_MESSAGE_PROCESS_DEFINITION_NOT_FOUND =
      "Expected to migrate process instance to process definition but no process definition found with key '%d'";

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final JobState jobState;

  public ProcessInstanceMigrationMigrateProcessor(
      final Writers writers,
      final ElementInstanceState elementInstanceState,
      final ProcessState processState,
      final JobState jobState) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
    this.jobState = jobState;
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
      migrateElementInstance(elementInstance, processDefinition, mappedElementIds);
      final List<ElementInstance> children =
          elementInstanceState.getChildren(elementInstance.getKey());
      elementInstances.addAll(children);
    }

    stateWriter.appendFollowUpEvent(
        processInstanceKey, ProcessInstanceMigrationIntent.MIGRATED, value);
    responseWriter.writeEventOnCommand(
        processInstanceKey, ProcessInstanceMigrationIntent.MIGRATED, value, command);
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

  private void migrateElementInstance(
      final ElementInstance elementInstance,
      final DeployedProcess processDefinition,
      final Map<String, String> sourceElementIdToTargetElementId) {

    final String targetElementId =
        sourceElementIdToTargetElementId.get(elementInstance.getValue().getElementId());

    stateWriter.appendFollowUpEvent(
        elementInstance.getKey(),
        ProcessInstanceIntent.ELEMENT_MIGRATED,
        elementInstance
            .getValue()
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
  }
}
