/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.instance;

import io.zeebe.engine.metrics.WorkflowEngineMetrics;
import io.zeebe.engine.processor.ReadonlyProcessingContext;
import io.zeebe.engine.processor.StreamProcessorLifecycleAware;
import io.zeebe.engine.processor.workflow.UpdateVariableStreamWriter;
import io.zeebe.engine.processor.workflow.WorkflowInstanceLifecycle;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.StoredRecord.Purpose;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public class WorkflowEngineState implements StreamProcessorLifecycleAware {

  private final WorkflowState workflowState;
  private ElementInstanceState elementInstanceState;
  private WorkflowEngineMetrics metrics;

  public WorkflowEngineState(WorkflowState workflowState) {
    this.workflowState = workflowState;
  }

  @Override
  public void onOpen(ReadonlyProcessingContext processingContext) {
    this.elementInstanceState = workflowState.getElementInstanceState();

    this.metrics = new WorkflowEngineMetrics(processingContext.getLogStream().getPartitionId());

    final UpdateVariableStreamWriter updateVariableStreamWriter =
        new UpdateVariableStreamWriter(processingContext.getLogStreamWriter());

    elementInstanceState.getVariablesState().setListener(updateVariableStreamWriter);
  }

  public void onEventProduced(
      long key, WorkflowInstanceIntent state, WorkflowInstanceRecord value) {

    if (WorkflowInstanceLifecycle.isElementInstanceState(state)) {
      onElementInstanceEventProduced(key, state, value);
    }
  }

  public void deferRecord(
      long key, long scopeKey, WorkflowInstanceRecord value, WorkflowInstanceIntent state) {
    elementInstanceState.storeRecord(key, scopeKey, value, state, Purpose.DEFERRED);
  }

  public void removeStoredRecord(long scopeKey, long key, Purpose purpose) {
    elementInstanceState.removeStoredRecord(scopeKey, key, purpose);
  }

  private void onElementInstanceEventProduced(
      long key, WorkflowInstanceIntent state, WorkflowInstanceRecord value) {

    // only instances that have a multi-state lifecycle are represented in the index
    if (WorkflowInstanceLifecycle.isInitialState(state)) {
      createNewElementInstance(key, state, value);
    } else {
      updateElementInstance(key, state, value);
    }

    recordMetrics(state, value);
  }

  private void updateElementInstance(
      long key, WorkflowInstanceIntent state, WorkflowInstanceRecord value) {
    final ElementInstance scopeInstance = elementInstanceState.getInstance(key);

    scopeInstance.setState(state);
    scopeInstance.setValue(value);
    elementInstanceState.updateInstance(scopeInstance);
  }

  private void createNewElementInstance(
      long key, WorkflowInstanceIntent state, WorkflowInstanceRecord value) {
    final long flowScopeKey = value.getFlowScopeKey();

    if (flowScopeKey >= 0) {
      final ElementInstance flowScopeInstance = elementInstanceState.getInstance(flowScopeKey);
      elementInstanceState.newInstance(flowScopeInstance, key, value, state);
    } else {
      elementInstanceState.newInstance(key, value, state);
    }
  }

  private void recordMetrics(WorkflowInstanceIntent state, WorkflowInstanceRecord value) {
    switch (state) {
      case ELEMENT_ACTIVATED:
        metrics.elementInstanceActivated(value.getBpmnElementType());
        break;
      case ELEMENT_COMPLETED:
        metrics.elementInstanceCompleted(value.getBpmnElementType());
        break;
      case ELEMENT_TERMINATED:
        metrics.elementInstanceTerminated(value.getBpmnElementType());
        break;
    }
  }

  public WorkflowState getWorkflowState() {
    return workflowState;
  }

  public ElementInstanceState getElementInstanceState() {
    return workflowState.getElementInstanceState();
  }

  public void storeFailedRecord(
      long key, WorkflowInstanceRecord recordValue, WorkflowInstanceIntent intent) {
    final long scopeKey = recordValue.getFlowScopeKey();
    elementInstanceState.storeRecord(key, scopeKey, recordValue, intent, Purpose.FAILED);
  }
}
