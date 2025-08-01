/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class ProcessInstanceElementMigratedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableElementInstanceState elementInstanceState;
  private ProcessInstanceElementMigratedV1Applier processInstanceElementMigratedApplier;

  @BeforeEach
  public void setup() {
    elementInstanceState = processingState.getElementInstanceState();
    processInstanceElementMigratedApplier =
        new ProcessInstanceElementMigratedV1Applier(elementInstanceState);
  }

  @Test
  void shouldUpdateProcessDefinitionData() {
    // given
    final var processInstance =
        new ProcessInstanceRecord()
            .setProcessDefinitionKey(1L)
            .setBpmnProcessId("process")
            .setVersion(1)
            .setElementId("process")
            .setBpmnElementType(BpmnElementType.PROCESS)
            .setProcessInstanceKey(2L);
    elementInstanceState.createInstance(
        new ElementInstance(
            processInstance.getProcessInstanceKey(),
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            processInstance));

    // when
    final var migratedProcessInstance = new ProcessInstanceRecord();
    migratedProcessInstance.wrap(processInstance);
    migratedProcessInstance
        .setProcessDefinitionKey(3L)
        .setVersion(2)
        .setBpmnProcessId("another_process")
        .setElementId("another_process");
    processInstanceElementMigratedApplier.applyState(
        processInstance.getProcessInstanceKey(), migratedProcessInstance);

    // then
    assertThat(elementInstanceState.getInstance(processInstance.getProcessInstanceKey()).getValue())
        .describedAs("Expect that the process definition data is updated")
        .hasProcessDefinitionKey(migratedProcessInstance.getProcessDefinitionKey())
        .hasBpmnProcessId(migratedProcessInstance.getBpmnProcessId())
        .hasVersion(migratedProcessInstance.getVersion())
        .hasElementId(migratedProcessInstance.getElementId())
        .describedAs("Expect that the other data is left unchanged")
        .hasProcessInstanceKey(processInstance.getProcessInstanceKey())
        .hasParentElementInstanceKey(processInstance.getParentElementInstanceKey())
        .hasParentProcessInstanceKey(processInstance.getParentProcessInstanceKey())
        .hasTenantId(processInstance.getTenantId())
        .hasBpmnElementType(processInstance.getBpmnElementType())
        .hasBpmnEventType(processInstance.getBpmnEventType())
        .hasFlowScopeKey(processInstance.getFlowScopeKey());

    assertThat(
            elementInstanceState.getProcessInstanceKeysByDefinitionKey(
                migratedProcessInstance.getProcessDefinitionKey()))
        .describedAs("Expect that the instance is migrated to the target process definition")
        .containsExactly(migratedProcessInstance.getProcessInstanceKey());
    assertThat(
            elementInstanceState.getProcessInstanceKeysByDefinitionKey(
                processInstance.getProcessDefinitionKey()))
        .describedAs("Expect that there are no instances of the old process definition")
        .isEmpty();
  }

  @Test
  void shouldUpdateFlowScopeKeyIfSetToDifferentValue() {
    // given
    final long serviceTaskKey = 3L;
    final var serviceTask =
        new ProcessInstanceRecord()
            .setProcessDefinitionKey(1L)
            .setBpmnProcessId("process")
            .setVersion(1)
            .setElementId("task")
            .setBpmnElementType(BpmnElementType.SERVICE_TASK)
            .setProcessInstanceKey(2L);
    elementInstanceState.createInstance(
        new ElementInstance(serviceTaskKey, ProcessInstanceIntent.ELEMENT_ACTIVATED, serviceTask));

    // when
    final var migratedServiceTask = new ProcessInstanceRecord();
    migratedServiceTask.wrap(serviceTask);
    migratedServiceTask
        .setProcessDefinitionKey(4L)
        .setVersion(2)
        .setBpmnProcessId("another_process")
        .setElementId("another_process")
        .setFlowScopeKey(5L);
    processInstanceElementMigratedApplier.applyState(serviceTaskKey, migratedServiceTask);

    // then
    assertThat(elementInstanceState.getInstance(serviceTaskKey).getValue())
        .describedAs("Expect that the flow scope key is updated")
        .hasFlowScopeKey(migratedServiceTask.getFlowScopeKey());
  }
}
