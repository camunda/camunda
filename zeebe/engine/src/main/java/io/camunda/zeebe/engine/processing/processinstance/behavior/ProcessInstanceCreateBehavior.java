/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance.behavior;

import io.camunda.zeebe.engine.processing.common.ElementActivationBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Set;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public class ProcessInstanceCreateBehavior {

  private final ProcessInstanceRecord newProcessInstance = new ProcessInstanceRecord();

  private final VariableBehavior variableBehavior;
  private final KeyGenerator keyGenerator;
  private final ElementActivationBehavior elementActivationBehavior;

  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;

  public ProcessInstanceCreateBehavior(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final ElementActivationBehavior elementActivationBehavior,
      final VariableBehavior variableBehavior) {
    this.keyGenerator = keyGenerator;
    this.variableBehavior = variableBehavior;
    this.elementActivationBehavior = elementActivationBehavior;
    commandWriter = writers.command();
    stateWriter = writers.state();
    responseWriter = writers.response();
  }

  public void createProcessInstance(
      final TypedRecord<ProcessInstanceCreationRecord> command,
      final ProcessInstanceCreationRecord record,
      final DeployedProcess process,
      final Consumer<TypedRecord<ProcessInstanceCreationRecord>> consumer) {
    final long processInstanceKey = keyGenerator.nextKey();

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

    consumer.accept(command);

    stateWriter.appendFollowUpEvent(
        processInstanceKey, ProcessInstanceCreationIntent.CREATED, record);
    responseWriter.writeEventOnCommand(
        processInstanceKey, ProcessInstanceCreationIntent.CREATED, record, command);
  }

  public void createProcessInstance(
      final TypedRecord<ProcessInstanceCreationRecord> command,
      final ProcessInstanceCreationRecord record,
      final DeployedProcess process) {
    createProcessInstance(command, record, process, ignored -> {});
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
