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
import java.util.List;

public final class WorkflowEngineState implements StreamProcessorLifecycleAware {

  private final WorkflowState workflowState;
  private final ElementInstanceState elementInstanceState;
  private final WorkflowEngineMetrics metrics;

  public WorkflowEngineState(final int partitionId, final WorkflowState workflowState) {
    this.workflowState = workflowState;
    this.elementInstanceState = workflowState.getElementInstanceState();
    this.metrics = new WorkflowEngineMetrics(partitionId);
  }

  @Override
  public void onRecovered(ReadonlyProcessingContext context) {
    final UpdateVariableStreamWriter updateVariableStreamWriter =
        new UpdateVariableStreamWriter(context.getLogStreamWriter());

    elementInstanceState.getVariablesState().setListener(updateVariableStreamWriter);
  }

  public void onEventProduced(
      final long key, final WorkflowInstanceIntent state, final WorkflowInstanceRecord value) {

    if (WorkflowInstanceLifecycle.isElementInstanceState(state)) {
      onElementInstanceEventProduced(key, state, value);
    }
  }

  public void deferRecord(
      final long key,
      final long scopeKey,
      final WorkflowInstanceRecord value,
      final WorkflowInstanceIntent state) {
    elementInstanceState.storeRecord(key, scopeKey, value, state, Purpose.DEFERRED);
  }

  public List<IndexedRecord> getDeferredRecords(final long scopeKey) {
    return elementInstanceState.getDeferredRecords(scopeKey);
  }

  public void removeStoredRecord(final long scopeKey, final long key, final Purpose purpose) {
    elementInstanceState.removeStoredRecord(scopeKey, key, purpose);
  }

  private void onElementInstanceEventProduced(
      final long key, final WorkflowInstanceIntent state, final WorkflowInstanceRecord value) {

    // only instances that have a multi-state lifecycle are represented in the index
    if (WorkflowInstanceLifecycle.isInitialState(state)) {
      createNewElementInstance(key, state, value);
    } else {
      updateElementInstance(key, state, value);
    }

    recordMetrics(state, value);
  }

  private void updateElementInstance(
      final long key, final WorkflowInstanceIntent state, final WorkflowInstanceRecord value) {
    final ElementInstance scopeInstance = elementInstanceState.getInstance(key);

    scopeInstance.setState(state);
    scopeInstance.setValue(value);
    elementInstanceState.updateInstance(scopeInstance);
  }

  private void createNewElementInstance(
      final long key, final WorkflowInstanceIntent state, final WorkflowInstanceRecord value) {
    final long flowScopeKey = value.getFlowScopeKey();

    if (flowScopeKey >= 0) {
      final ElementInstance flowScopeInstance = elementInstanceState.getInstance(flowScopeKey);
      elementInstanceState.newInstance(flowScopeInstance, key, value, state);
    } else {
      elementInstanceState.newInstance(key, value, state);
    }
  }

  private void recordMetrics(
      final WorkflowInstanceIntent state, final WorkflowInstanceRecord value) {
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
      default:
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
      final long key,
      final WorkflowInstanceRecord recordValue,
      final WorkflowInstanceIntent intent) {
    final long scopeKey = recordValue.getFlowScopeKey();
    elementInstanceState.storeRecord(key, scopeKey, recordValue, intent, Purpose.FAILED);
  }
}
