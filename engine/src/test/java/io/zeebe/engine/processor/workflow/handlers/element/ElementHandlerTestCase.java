/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.element;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.EventOutput;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.engine.state.instance.IndexedRecord;
import io.zeebe.engine.state.instance.StoredRecord;
import io.zeebe.engine.state.instance.StoredRecord.Purpose;
import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import org.junit.Before;
import org.junit.ClassRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

public abstract class ElementHandlerTestCase<T extends ExecutableFlowNode> {
  @ClassRule public static ZeebeStateRule zeebeStateRule = new ZeebeStateRule();

  @Mock public EventOutput eventOutput;
  @Mock public TypedStreamWriter streamWriter;
  @Captor public ArgumentCaptor<IncidentRecord> incidentCaptor;

  protected BpmnStepContext<T> context;
  protected ElementInstanceState elementInstanceState;

  @Before
  public void setUp() {
    context = new BpmnStepContext<>(zeebeStateRule.getZeebeState().getWorkflowState(), eventOutput);
    elementInstanceState =
        zeebeStateRule.getZeebeState().getWorkflowState().getElementInstanceState();
    context.setStreamWriter(streamWriter);
  }

  protected IncidentRecord getRaisedIncident() {
    verifyIncidentRaised();
    return incidentCaptor.getValue();
  }

  protected void verifyIncidentRaised() {
    verify(eventOutput, times(1))
        .storeFailedRecord(context.getKey(), context.getValue(), context.getState());
    verify(streamWriter, times(1))
        .appendNewCommand(eq(IncidentIntent.CREATE), incidentCaptor.capture());
  }

  protected void verifyRecordPublished(StoredRecord record, long flowScopeKey) {
    final WorkflowInstanceRecord expectedRecord = new WorkflowInstanceRecord();
    expectedRecord.wrap(record.getRecord().getValue());
    expectedRecord.setFlowScopeKey(flowScopeKey);

    verify(eventOutput, times(1))
        .appendFollowUpEvent(record.getKey(), record.getRecord().getState(), expectedRecord);
  }

  protected ElementInstance createAndSetContextElementInstance(WorkflowInstanceIntent state) {
    final ElementInstance instance = newElementInstance(state);
    setContextElementInstance(instance);

    return instance;
  }

  protected ElementInstance createAndSetContextElementInstance(
      WorkflowInstanceIntent state, ElementInstance flowScope) {
    final ElementInstance instance = newElementInstance(state, flowScope);
    setContextElementInstance(instance);

    return instance;
  }

  protected void setContextElementInstance(ElementInstance instance) {
    context.init(instance.getKey(), instance.getValue(), instance.getState());
  }

  protected ElementInstance newElementInstance(WorkflowInstanceIntent state) {
    final long key = zeebeStateRule.getKeyGenerator().nextKey();
    final WorkflowInstanceRecord value = new WorkflowInstanceRecord();

    return zeebeStateRule
        .getZeebeState()
        .getWorkflowState()
        .getElementInstanceState()
        .newInstance(key, value, state);
  }

  protected ElementInstance newElementInstance(
      WorkflowInstanceIntent state, ElementInstance flowScope) {
    final long key = zeebeStateRule.getKeyGenerator().nextKey();
    final WorkflowInstanceRecord value = new WorkflowInstanceRecord();
    value.setFlowScopeKey(flowScope.getKey());
    flowScope.spawnToken();

    return zeebeStateRule
        .getZeebeState()
        .getWorkflowState()
        .getElementInstanceState()
        .newInstance(flowScope, key, value, state);
  }

  protected StoredRecord deferRecordOn(ElementInstance scopeInstance) {
    final long key = zeebeStateRule.getKeyGenerator().nextKey();
    final WorkflowInstanceRecord value =
        new WorkflowInstanceRecord().setFlowScopeKey(scopeInstance.getKey());
    final IndexedRecord indexedRecord =
        new IndexedRecord(key, WorkflowInstanceIntent.ELEMENT_ACTIVATING, value);
    final StoredRecord storedRecord = new StoredRecord(indexedRecord, Purpose.DEFERRED);

    zeebeStateRule
        .getZeebeState()
        .getWorkflowState()
        .getElementInstanceState()
        .storeRecord(
            key,
            scopeInstance.getKey(),
            indexedRecord.getValue(),
            indexedRecord.getState(),
            storedRecord.getPurpose());

    return storedRecord;
  }
}
