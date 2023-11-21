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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue.ProcessInstanceMigrationMappingInstructionValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayDeque;
import java.util.List;

public class ProcessInstanceMigrationMigrateProcessor
    implements TypedRecordProcessor<ProcessInstanceMigrationRecord> {

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;

  public ProcessInstanceMigrationMigrateProcessor(
      final Writers writers,
      final ElementInstanceState elementInstanceState,
      final ProcessState processState) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceMigrationRecord> command) {
    final ProcessInstanceMigrationRecord value = command.getValue();
    final long processInstanceKey = value.getProcessInstanceKey();
    final long targetProcessDefinitionKey = value.getTargetProcessDefinitionKey();
    final var mappingInstructions = value.getMappingInstructions();

    final ElementInstance processInstance = elementInstanceState.getInstance(processInstanceKey);
    if (processInstance == null) {
      // todo: we should reject the command explicitly
      throw new IllegalArgumentException(
          String.format(
              "Expected to migrate process instance with key '%d', but process instance not found",
              processInstanceKey));
    }

    final DeployedProcess processDefinition =
        processState.getProcessByKeyAndTenant(
            targetProcessDefinitionKey, processInstance.getValue().getTenantId());
    if (processDefinition == null) {
      // todo: we should reject the command explicitly
      throw new IllegalStateException(
          String.format(
              "Expected to migrate process instance with key '%d' to process definition with key '%d', but process definition not found",
              value.getProcessInstanceKey(), targetProcessDefinitionKey));
    }

    // avoid stackoverflow using a queue to iterate over the descendants instead of recursion
    final var elementInstances = new ArrayDeque<>(List.of(processInstance));
    while (!elementInstances.isEmpty()) {
      final var elementInstance = elementInstances.poll();
      final List<ElementInstance> children =
          migrateElementInstance(elementInstance, processDefinition, mappingInstructions);
      elementInstances.addAll(children);
    }

    stateWriter.appendFollowUpEvent(
        processInstanceKey, ProcessInstanceMigrationIntent.MIGRATED, value);
    responseWriter.writeEventOnCommand(
        processInstanceKey, ProcessInstanceMigrationIntent.MIGRATED, value, command);
  }

  private List<ElementInstance> migrateElementInstance(
      final ElementInstance elementInstance,
      final DeployedProcess processDefinition,
      final List<ProcessInstanceMigrationMappingInstructionValue> mappingInstructions) {

    final String targetElementId =
        determineTargetElementId(elementInstance, processDefinition, mappingInstructions);

    stateWriter.appendFollowUpEvent(
        elementInstance.getKey(),
        ProcessInstanceIntent.ELEMENT_MIGRATED,
        elementInstance
            .getValue()
            .setProcessDefinitionKey(processDefinition.getKey())
            .setBpmnProcessId(processDefinition.getBpmnProcessId())
            .setVersion(processDefinition.getVersion())
            .setElementId(targetElementId));

    return elementInstanceState.getChildren(elementInstance.getKey());
  }

  private static String determineTargetElementId(
      final ElementInstance elementInstance,
      final DeployedProcess targetProcessDefinition,
      final List<ProcessInstanceMigrationMappingInstructionValue> mappingInstructions) {
    if (elementInstance.getValue().getBpmnElementType() == BpmnElementType.PROCESS) {
      // users don't provide a mapping instruction for the bpmn process id
      return BufferUtil.bufferAsString(targetProcessDefinition.getBpmnProcessId());
    }
    final String elementId = elementInstance.getValue().getElementId();
    return mappingInstructions.stream()
        .filter(instruction -> instruction.getSourceElementId().equals(elementId))
        .map(ProcessInstanceMigrationMappingInstructionValue::getTargetElementId)
        .findAny() // highlights that mappings could share the same source element id
        .orElseThrow();
  }
}
