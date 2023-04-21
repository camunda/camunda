/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.common.ElementActivationBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.ArrayList;
import java.util.List;

public class ProcessInstanceMigrationProcessor
    implements TypedRecordProcessor<ProcessInstanceMigrationRecord> {

  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final KeyGenerator keyGenerator;
  private final ElementActivationBehavior elementActivationBehavior;

  public ProcessInstanceMigrationProcessor(
      final ElementInstanceState elementInstanceState,
      final ProcessState processState,
      final KeyGenerator keyGenerator,
      final ElementActivationBehavior elementActivationBehavior) {
    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
    this.keyGenerator = keyGenerator;
    this.elementActivationBehavior = elementActivationBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceMigrationRecord> record) {
    // HAPPY PATH :)
    final var command = record.getValue();

    // TODO getting process instance from the state
    final var processInstance = elementInstanceState.getInstance(command.getProcessInstanceKey());

    // TODO Get the target process definition
    final var targetProcess =
        processState.getProcessByKey(command.getMigrationPlan().targetProcessDefinitionKey());
    //            .getProcess();

    // TODO cancel the current process instance through events, not commands
    // Magic!

    // TODO start the new process instance
    // create the new process instance
    final var processInstanceKey = keyGenerator.nextKey();
    final var processInstanceRecord =
        createProcessInstanceRecord(targetProcess, processInstanceKey);

    // find active element instances in the process instance (not implemented for now)
    final List<ElementInstance> activeChildElementInstances = new ArrayList<>();

    // map them according to the mapping instruction
    final var mappingInstructions = command.getMigrationPlan().mappingInstructions();
    activeChildElementInstances.stream()
        .map(child -> child.getValue().getElementId())
        .map(
            childElementId ->
                mappingInstructions.stream()
                    .filter(instruction -> instruction.sourceElementId().equals(childElementId))
                    .map(i -> i.targetElementId())
                    .findFirst()
                    .orElseThrow())
        .forEach(
            targetToActivate -> {
              // TODO activate the new elements
              final var elementToActivate =
                  targetProcess.getProcess().getElementById(targetToActivate);
              elementActivationBehavior.activateElement(processInstanceRecord, elementToActivate);
            });

    // TODO write PI migrated event and response
  }

  private static ProcessInstanceRecord createProcessInstanceRecord(
      final DeployedProcess targetProcess, final long processInstanceKey) {
    final var processInstanceRecord = new ProcessInstanceRecord();
    processInstanceRecord.setBpmnProcessId(targetProcess.getBpmnProcessId());
    processInstanceRecord.setVersion(targetProcess.getVersion());
    processInstanceRecord.setProcessDefinitionKey(targetProcess.getKey());
    processInstanceRecord.setProcessInstanceKey(processInstanceKey);
    processInstanceRecord.setBpmnElementType(BpmnElementType.PROCESS);
    processInstanceRecord.setElementId(targetProcess.getProcess().getId());
    processInstanceRecord.setFlowScopeKey(-1);
    return processInstanceRecord;
  }
}
