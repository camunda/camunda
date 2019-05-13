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
package io.zeebe.engine.processor.workflow;

import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.state.instance.StoredRecord.Purpose;
import io.zeebe.engine.state.instance.WorkflowEngineState;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class EventOutput {
  private final WorkflowEngineState materializedState;
  private final KeyGenerator keyGenerator;

  private TypedStreamWriter streamWriter;

  public EventOutput(final WorkflowEngineState materializedState, final KeyGenerator keyGenerator) {
    this.materializedState = materializedState;
    this.keyGenerator = keyGenerator;
  }

  public void setStreamWriter(final TypedStreamWriter streamWriter) {
    this.streamWriter = streamWriter;
  }

  public long appendNewEvent(
      final WorkflowInstanceIntent state, final WorkflowInstanceRecord value) {
    return appendNewEvent(state, value, null);
  }

  public long appendNewEvent(
      final WorkflowInstanceIntent state,
      final WorkflowInstanceRecord value,
      final ExecutableFlowElement element) {

    if (element != null) {
      value.setElementId(element.getId());
      value.setBpmnElementType(element.getElementType());
    }

    final long key = keyGenerator.nextKey();
    streamWriter.appendNewEvent(key, state, value);
    materializedState.onEventProduced(key, state, value);

    return key;
  }

  public void appendFollowUpEvent(
      final long key, final WorkflowInstanceIntent state, final WorkflowInstanceRecord value) {
    appendFollowUpEvent(key, state, value, null);
  }

  public void appendFollowUpEvent(
      final long key,
      final WorkflowInstanceIntent state,
      final WorkflowInstanceRecord value,
      final ExecutableFlowElement element) {
    if (element != null) {
      value.setElementId(element.getId());
      value.setBpmnElementType(element.getElementType());
    }

    streamWriter.appendFollowUpEvent(key, state, value);

    materializedState.onEventProduced(key, state, value);
  }

  public void appendResolvedIncidentEvent(
      final long incidentKey, final IncidentRecord incidentRecord) {
    streamWriter.appendFollowUpEvent(incidentKey, IncidentIntent.RESOLVED, incidentRecord);
  }

  public long deferEvent(final TypedRecord<WorkflowInstanceRecord> event) {
    final WorkflowInstanceRecord value = event.getValue();
    final WorkflowInstanceIntent intent = (WorkflowInstanceIntent) event.getMetadata().getIntent();

    return deferRecord(value.getFlowScopeKey(), value, intent);
  }

  public long deferRecord(
      long scopeKey, WorkflowInstanceRecord value, WorkflowInstanceIntent intent) {
    final long elementInstanceKey = keyGenerator.nextKey();
    materializedState.deferRecord(elementInstanceKey, scopeKey, value, intent);

    return elementInstanceKey;
  }

  public void storeFailedRecord(final TypedRecord<WorkflowInstanceRecord> event) {
    materializedState.storeFailedRecord(event);
  }

  public void removeDeferredEvent(final long scopeKey, final long key) {
    materializedState.removeStoredRecord(scopeKey, key, Purpose.DEFERRED);
  }

  public TypedStreamWriter getStreamWriter() {
    return streamWriter;
  }
}
