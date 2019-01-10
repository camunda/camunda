/*
 * Zeebe Broker Core
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
package io.zeebe.broker.workflow.processor;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.workflow.state.StoredRecord.Purpose;
import io.zeebe.broker.workflow.state.WorkflowEngineState;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class EventOutput {
  private final WorkflowEngineState materializedState;

  private TypedStreamWriter streamWriter;

  public EventOutput(final WorkflowEngineState materializedState) {
    this.materializedState = materializedState;
  }

  public void setStreamWriter(final TypedStreamWriter streamWriter) {
    this.streamWriter = streamWriter;
  }

  public long appendNewEvent(
      final WorkflowInstanceIntent state, final WorkflowInstanceRecord value) {
    final long key;
    key = streamWriter.appendNewEvent(state, value);

    materializedState.onEventProduced(key, state, value);

    return key;
  }

  public void appendFollowUpEvent(
      final long key, final WorkflowInstanceIntent state, final WorkflowInstanceRecord value) {

    streamWriter.appendFollowUpEvent(key, state, value);

    materializedState.onEventProduced(key, state, value);
  }

  public void appendResolvedIncidentEvent(
      final long incidentKey, final IncidentRecord incidentRecord) {
    streamWriter.appendFollowUpEvent(incidentKey, IncidentIntent.RESOLVED, incidentRecord);
  }

  public void deferEvent(final TypedRecord<WorkflowInstanceRecord> event) {
    materializedState.deferRecord(event);
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
