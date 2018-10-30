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

import io.zeebe.broker.logstreams.processor.TypedBatchWriter;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.workflow.state.StoredRecord.Purpose;
import io.zeebe.broker.workflow.state.WorkflowEngineState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class EventOutput {
  private final WorkflowEngineState materializedState;
  private TypedBatchWriter batchWriter;

  public EventOutput(WorkflowEngineState materializedState) {
    this.materializedState = materializedState;
  }

  public void setStreamWriter(TypedStreamWriter streamWriter) {
    this.batchWriter = streamWriter.newBatch();
  }

  /**
   * Allows to add non-workflow records to the batch. Ideally we can get rid of this when all
   * entities are managed by one stream processor. We would then add methods with signatures like
   * <code>writeFollowUpEvent(long key, JobIntent state, JobRecord value)</code>, and then we can
   * transparently register the job in the index.
   */
  public TypedBatchWriter getBatchWriter() {
    return batchWriter;
  }

  public long writeNewEvent(WorkflowInstanceIntent state, WorkflowInstanceRecord value) {
    final long key = batchWriter.addNewEvent(state, value);
    materializedState.onEventProduced(key, state, value);

    return key;
  }

  public void writeFollowUpEvent(
      long key, WorkflowInstanceIntent state, WorkflowInstanceRecord value) {
    batchWriter.addFollowUpEvent(key, state, value);
    materializedState.onEventProduced(key, state, value);
  }

  public void deferEvent(final TypedRecord<WorkflowInstanceRecord> event) {
    materializedState.deferTokenEvent(event);
  }

  public void storeFailedToken(final TypedRecord<WorkflowInstanceRecord> event) {
    materializedState.storeFailedToken(event);
  }

  public void storeFinishedToken(final TypedRecord<WorkflowInstanceRecord> event) {
    materializedState.storeFinishedToken(event);
  }

  public void consumeDeferredEvent(long scopeKey, long key) {
    materializedState.consumeStoredRecord(scopeKey, key, Purpose.DEFERRED_TOKEN);
  }

  public void consumeFinishedToken(long scopeKey, long key) {
    materializedState.consumeStoredRecord(scopeKey, key, Purpose.FINISHED_TOKEN);
  }
}
