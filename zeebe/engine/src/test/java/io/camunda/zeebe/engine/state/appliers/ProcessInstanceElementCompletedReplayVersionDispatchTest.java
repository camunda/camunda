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
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
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
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Exercises the real {@link EventAppliers#applyState} version-dispatch path: a persisted
 * `ProcessInstance:Element_Completed` record for a child process instance must dispatch to the
 * applier matching its own stored {@code recordVersion}, not the latest registered one, so
 * historical behavior (variables silently discarded) is preserved for old records while new records
 * get the fix (variables parked and mapped locally).
 */
@ExtendWith(ProcessingStateExtension.class)
final class ProcessInstanceElementCompletedReplayVersionDispatchTest {

  private static final String PARENT_PROCESS_ID = "parent";
  private static final String CHILD_PROCESS_ID = "child";
  private static final String CALL_ACTIVITY_ID = "call";
  private static final String VARIABLE_NAME = "x";

  private MutableProcessingState processingState;

  private MutableElementInstanceState elementInstanceState;
  private MutableEventScopeInstanceState eventScopeInstanceState;
  private MutableVariableState variableState;
  private KeyGenerator keyGenerator;

  private EventAppliers eventAppliers;
  private long callActivityInstanceKey;
  private long childProcessDefinitionKey;

  @BeforeEach
  void setUp() {
    elementInstanceState = processingState.getElementInstanceState();
    eventScopeInstanceState = processingState.getEventScopeInstanceState();
    variableState = processingState.getVariableState();
    keyGenerator = processingState.getKeyGenerator();

    eventAppliers = new EventAppliers();
    eventAppliers.registerEventAppliers(processingState);

    // given: a deployed process with a call activity that has propagateAllChildVariables=false
    // and no output mappings
    final BpmnModelInstance parentProcess =
        Bpmn.createExecutableProcess(PARENT_PROCESS_ID)
            .startEvent()
            .callActivity(
                CALL_ACTIVITY_ID,
                c -> c.zeebeProcessId(CHILD_PROCESS_ID).zeebePropagateAllChildVariables(false))
            .endEvent()
            .done();
    final long processDefinitionKey = keyGenerator.nextKey();
    deployProcess(PARENT_PROCESS_ID, parentProcess, processDefinitionKey);

    final BpmnModelInstance childProcess =
        Bpmn.createExecutableProcess(CHILD_PROCESS_ID).startEvent().endEvent().done();
    childProcessDefinitionKey = keyGenerator.nextKey();
    deployProcess(CHILD_PROCESS_ID, childProcess, childProcessDefinitionKey);

    final long parentProcessInstanceKey = keyGenerator.nextKey();
    callActivityInstanceKey = keyGenerator.nextKey();
    final var callActivityRecord =
        new ProcessInstanceRecord()
            .setProcessDefinitionKey(processDefinitionKey)
            .setBpmnProcessId(PARENT_PROCESS_ID)
            .setVersion(1)
            .setElementId(CALL_ACTIVITY_ID)
            .setBpmnElementType(BpmnElementType.CALL_ACTIVITY)
            .setProcessInstanceKey(parentProcessInstanceKey);
    elementInstanceState.createInstance(
        new ElementInstance(
            callActivityInstanceKey, ProcessInstanceIntent.ELEMENT_ACTIVATED, callActivityRecord));
    eventScopeInstanceState.createInstance(
        callActivityInstanceKey, Collections.emptyList(), Collections.emptyList());
  }

  private void deployProcess(
      final String processId, final BpmnModelInstance model, final long processDefinitionKey) {
    final ProcessRecord processRecord =
        new ProcessRecord()
            .setResourceName(wrapString(processId + ".bpmn"))
            .setResource(wrapString(Bpmn.convertToString(model)))
            .setBpmnProcessId(BufferUtil.wrapString(processId))
            .setVersion(1)
            .setKey(processDefinitionKey)
            .setChecksum(wrapString("checksum"))
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setDeploymentKey(keyGenerator.nextKey());
    eventAppliers.applyState(
        processDefinitionKey,
        ProcessIntent.CREATED,
        processRecord,
        eventAppliers.getLatestVersion(ProcessIntent.CREATED));
  }

  private ProcessInstanceRecord completingChildProcessInstanceRecord(final long childInstanceKey) {
    variableState.setVariableLocal(
        keyGenerator.nextKey(),
        childInstanceKey,
        childProcessDefinitionKey,
        wrapString(VARIABLE_NAME),
        MsgPackUtil.asMsgPack("1"));

    return new ProcessInstanceRecord()
        .setProcessDefinitionKey(childProcessDefinitionKey)
        .setBpmnProcessId(CHILD_PROCESS_ID)
        .setVersion(1)
        .setElementId(CHILD_PROCESS_ID)
        .setBpmnElementType(BpmnElementType.PROCESS)
        .setProcessInstanceKey(childInstanceKey)
        .setParentElementInstanceKey(callActivityInstanceKey);
  }

  @Test
  void shouldDiscardChildVariablesOnReplayOfPreExistingVersion() {
    // given: a child process COMPLETED record stamped with the latest registered
    // ELEMENT_COMPLETED version before this fix, version 2
    final long childInstanceKey = keyGenerator.nextKey();
    final var childRecord = completingChildProcessInstanceRecord(childInstanceKey);

    // when: replayed via the real dispatch path using the record's own stored version
    eventAppliers.applyState(
        childInstanceKey, ProcessInstanceIntent.ELEMENT_COMPLETED, childRecord, 2);

    // then: the child's variable must not be parked at the call activity's event scope, matching
    // what the V2 applier actually did (discarding it)
    assertThat(eventScopeInstanceState.peekEventTrigger(callActivityInstanceKey))
        .as("recordVersion=2 (ProcessInstanceElementCompletedV2Applier) must not park variables")
        .isNull();
  }

  @Test
  void shouldParkChildVariablesOnReplayOfNewVersion() {
    // given: a child process COMPLETED record stamped with this fix's new version
    final long childInstanceKey = keyGenerator.nextKey();
    final var childRecord = completingChildProcessInstanceRecord(childInstanceKey);

    // when: replayed via the real dispatch path using the record's own stored version
    eventAppliers.applyState(
        childInstanceKey, ProcessInstanceIntent.ELEMENT_COMPLETED, childRecord, 3);

    // then: the child's variable must be parked at the call activity's event scope so that
    // BpmnVariableMappingBehavior can map it locally instead of discarding it
    final var trigger = eventScopeInstanceState.peekEventTrigger(callActivityInstanceKey);
    assertThat(trigger)
        .as("recordVersion=3 (ProcessInstanceElementCompletedV3Applier) must park variables")
        .isNotNull();
    MsgPackUtil.assertEquality(trigger.getVariables(), "{\"" + VARIABLE_NAME + "\":1}");
  }
}
