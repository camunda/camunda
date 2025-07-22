/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class ProcessInstanceSequenceFlowDeletedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableElementInstanceState elementInstanceState;
  private MutableProcessState processState;
  private ProcessInstanceSequenceFlowDeletedApplier sequenceFlowDeletedApplier;

  /** For setting up the state before testing the applier. */
  private AppliersTestSetupHelper testSetup;

  @BeforeEach
  public void setup() {
    elementInstanceState = processingState.getElementInstanceState();
    processState = processingState.getProcessState();
    sequenceFlowDeletedApplier =
        new ProcessInstanceSequenceFlowDeletedApplier(elementInstanceState, processState);
    testSetup = new AppliersTestSetupHelper(processingState);
  }

  @Test
  void shouldDecrementSequenceFlowFromFlowScope() {
    // given
    final String processId = "process";
    final String serviceTaskElementId = "task";
    final String sequenceFlowElementId = "flow1";
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(processId)
            .startEvent("startEvent")
            .serviceTask(serviceTaskElementId, task -> task.zeebeJobType("type"))
            .sequenceFlowId(sequenceFlowElementId)
            .endEvent("endEvent")
            .done();
    final ProcessRecord processRecord = new ProcessRecord();
    final String resourceName = "process.bpmn";
    final var resource = wrapString(Bpmn.convertToString(modelInstance));
    final var checksum = wrapString("checksum");

    final KeyGenerator keyGenerator = processingState.getKeyGenerator();
    final long processDefinitionKey = keyGenerator.nextKey();

    processRecord
        .setResourceName(wrapString(resourceName))
        .setResource(resource)
        .setBpmnProcessId(BufferUtil.wrapString(processId))
        .setVersion(1)
        .setKey(processDefinitionKey)
        .setResourceName(resourceName)
        .setChecksum(checksum)
        .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .setDeploymentKey(keyGenerator.nextKey());

    testSetup.applyEventToState(processDefinitionKey, ProcessIntent.CREATED, processRecord);

    final long serviceTaskKey = keyGenerator.nextKey();
    final long processInstanceKey = keyGenerator.nextKey();
    final var serviceTask =
        new ProcessInstanceRecord()
            .setProcessDefinitionKey(processDefinitionKey)
            .setBpmnProcessId(processId)
            .setVersion(1)
            .setElementId(serviceTaskElementId)
            .setBpmnElementType(BpmnElementType.SERVICE_TASK)
            .setProcessInstanceKey(processInstanceKey);
    elementInstanceState.createInstance(
        new ElementInstance(serviceTaskKey, ProcessInstanceIntent.ELEMENT_ACTIVATED, serviceTask));

    final var sequenceFlowRecord =
        new ProcessInstanceRecord()
            .setElementId(sequenceFlowElementId)
            .setBpmnElementType(BpmnElementType.SEQUENCE_FLOW)
            .setBpmnProcessId(processId)
            .setVersion(1)
            .setProcessDefinitionKey(processDefinitionKey)
            .setProcessInstanceKey(processInstanceKey)
            .setFlowScopeKey(serviceTaskKey);

    testSetup.applyEventToState(
        keyGenerator.nextKey(), ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN, sequenceFlowRecord);

    // when
    sequenceFlowDeletedApplier.applyState(keyGenerator.nextKey(), sequenceFlowRecord);

    // then
    final ElementInstance serviceTaskInstance = elementInstanceState.getInstance(serviceTaskKey);
    assertThat(serviceTaskInstance.getActiveSequenceFlows())
        .describedAs("Expect that the sequence flow is removed from the service task")
        .isEqualTo(0);
    assertThat(serviceTaskInstance.getActiveSequenceFlowIds())
        .describedAs("Expect that the sequence flow is removed from the service task")
        .isEmpty();
  }

  @Test
  void shouldDecrementSequenceFlowFromJoiningGateway() {
    // given
    final String processId = "process";
    final String gatewayElementId = "join1";
    final String serviceTaskElementId = "task1";
    final String sequenceFlowElementId = "flow1";
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .parallelGateway("fork")
            .serviceTask(serviceTaskElementId, b -> b.zeebeJobType("type1"))
            .sequenceFlowId(sequenceFlowElementId)
            .parallelGateway(gatewayElementId)
            .endEvent()
            .moveToNode("fork")
            .serviceTask("task2", b -> b.zeebeJobType("type2"))
            .connectTo(gatewayElementId)
            .done();
    final ProcessRecord processRecord = new ProcessRecord();
    final String resourceName = "process.bpmn";
    final var resource = wrapString(Bpmn.convertToString(modelInstance));
    final var checksum = wrapString("checksum");

    final KeyGenerator keyGenerator = processingState.getKeyGenerator();
    final long key = keyGenerator.nextKey();

    processRecord
        .setResourceName(wrapString(resourceName))
        .setResource(resource)
        .setBpmnProcessId(BufferUtil.wrapString(processId))
        .setVersion(1)
        .setKey(key)
        .setResourceName(resourceName)
        .setChecksum(checksum)
        .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .setDeploymentKey(keyGenerator.nextKey());

    testSetup.applyEventToState(key, ProcessIntent.CREATED, processRecord);

    final long serviceTaskKey = keyGenerator.nextKey();
    final long processInstanceKey = keyGenerator.nextKey();
    final var serviceTask =
        new ProcessInstanceRecord()
            .setProcessDefinitionKey(key)
            .setBpmnProcessId(processId)
            .setVersion(1)
            .setElementId(serviceTaskElementId)
            .setBpmnElementType(BpmnElementType.SERVICE_TASK)
            .setProcessInstanceKey(processInstanceKey);
    elementInstanceState.createInstance(
        new ElementInstance(serviceTaskKey, ProcessInstanceIntent.ELEMENT_ACTIVATED, serviceTask));

    final long gatewayKey = keyGenerator.nextKey();
    final var gateway =
        new ProcessInstanceRecord()
            .setProcessDefinitionKey(key)
            .setBpmnProcessId(processId)
            .setVersion(1)
            .setElementId(gatewayElementId)
            .setBpmnElementType(BpmnElementType.PARALLEL_GATEWAY)
            .setProcessInstanceKey(processInstanceKey);
    elementInstanceState.createInstance(
        new ElementInstance(gatewayKey, ProcessInstanceIntent.ELEMENT_ACTIVATED, gateway));

    final var sequenceFlowRecord =
        new ProcessInstanceRecord()
            .setElementId(sequenceFlowElementId)
            .setBpmnElementType(BpmnElementType.SEQUENCE_FLOW)
            .setBpmnProcessId(processId)
            .setVersion(1)
            .setProcessDefinitionKey(key)
            .setProcessInstanceKey(processInstanceKey)
            .setFlowScopeKey(serviceTaskKey);

    testSetup.applyEventToState(
        keyGenerator.nextKey(), ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN, sequenceFlowRecord);

    // when
    sequenceFlowDeletedApplier.applyState(keyGenerator.nextKey(), sequenceFlowRecord);

    // then
    final ElementInstance serviceTaskInstance = elementInstanceState.getInstance(serviceTaskKey);
    assertThat(serviceTaskInstance.getActiveSequenceFlows())
        .describedAs("Expect that the sequence flow is removed from the service task")
        .isEqualTo(0);
    assertThat(serviceTaskInstance.getActiveSequenceFlowIds())
        .describedAs("Expect that the sequence flow is removed from the service task")
        .isEmpty();

    final int numberOfTakenSequenceFlows =
        elementInstanceState.getNumberOfTakenSequenceFlows(
            processInstanceKey, wrapString(gatewayElementId));
    assertThat(numberOfTakenSequenceFlows)
        .describedAs("Expect that the sequence flow is removed from the gateway")
        .isEqualTo(0);
  }
}
