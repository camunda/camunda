/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.state.instance;

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

  public WorkflowEngineState(WorkflowState workflowState) {
    this.workflowState = workflowState;
  }

  @Override
  public void onOpen(ReadonlyProcessingContext processingContext) {
    this.elementInstanceState = workflowState.getElementInstanceState();

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
