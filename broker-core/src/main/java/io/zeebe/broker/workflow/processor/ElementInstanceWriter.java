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
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.broker.workflow.index.ElementInstance;
import io.zeebe.broker.workflow.index.ElementInstanceIndex;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class ElementInstanceWriter {

  private final ElementInstanceIndex scopeInstances;
  private final WorkflowInstanceMetrics metrics;

  private TypedStreamWriter streamWriter;
  private TypedBatchWriter batchWriter;

  public ElementInstanceWriter(
      ElementInstanceIndex scopeInstances, WorkflowInstanceMetrics metrics) {
    this.scopeInstances = scopeInstances;
    this.metrics = metrics;
  }

  public void setStreamWriter(TypedStreamWriter streamWriter) {
    this.streamWriter = streamWriter;
    this.batchWriter = null;
  }

  /**
   * Ideally we can get rid of this. Calling code should not need to declare upfront how many
   * records it writes.
   */
  public void newBatch() {
    batchWriter = streamWriter.newBatch();
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
    final long key;
    if (batchWriter != null) {
      key = batchWriter.addNewEvent(state, value);
    } else {
      key = streamWriter.writeNewEvent(state, value);
    }

    // only instances that have a multi-state lifecycle are represented in the index
    if (WorkflowInstanceLifecycle.isInitialState(state)) {
      final long scopeInstanceKey = value.getScopeInstanceKey();

      if (scopeInstanceKey >= 0) {
        final ElementInstance flowScopeInstance = scopeInstances.getInstance(scopeInstanceKey);
        scopeInstances.newInstance(flowScopeInstance, key, value, state);
      } else {
        scopeInstances.newInstance(key, value, state);
      }
    }

    return key;
  }

  public void writeFollowUpEvent(
      long key, WorkflowInstanceIntent state, WorkflowInstanceRecord value) {
    if (batchWriter != null) {
      batchWriter.addFollowUpEvent(key, state, value);
    } else {
      streamWriter.writeFollowUpEvent(key, state, value);
    }

    if (WorkflowInstanceLifecycle.isFinalState(state)) {
      scopeInstances.removeInstance(key);
    } else {
      final ElementInstance scopeInstance = scopeInstances.getInstance(key);
      scopeInstance.setState(state);
      scopeInstance.setValue(value);
    }

    if (key == value.getWorkflowInstanceKey()) {
      if (state == WorkflowInstanceIntent.ELEMENT_TERMINATED) {
        metrics.countInstanceCanceled();
      } else if (state == WorkflowInstanceIntent.ELEMENT_COMPLETED) {
        metrics.coundInstanceCompleted();
      }
    }
  }
}
