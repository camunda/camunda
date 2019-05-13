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
package io.zeebe.engine.processor.workflow.incident;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.state.instance.IndexedRecord;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;

public class TypedWorkflowInstanceRecord implements TypedRecord<WorkflowInstanceRecord> {

  private final RecordMetadata recordMetadata;

  private IndexedRecord persistedWorkflowInstanceRecord;

  public TypedWorkflowInstanceRecord() {
    recordMetadata = new RecordMetadata();
    recordMetadata.recordType(RecordType.EVENT);
    recordMetadata.valueType(ValueType.WORKFLOW_INSTANCE);
  }

  public void wrap(IndexedRecord persistedWorkflowInstanceRecord) {
    this.persistedWorkflowInstanceRecord = persistedWorkflowInstanceRecord;
    recordMetadata.intent(persistedWorkflowInstanceRecord.getState());
  }

  @Override
  public long getKey() {
    return persistedWorkflowInstanceRecord.getKey();
  }

  @Override
  public RecordMetadata getMetadata() {
    return recordMetadata;
  }

  @Override
  public WorkflowInstanceRecord getValue() {
    return persistedWorkflowInstanceRecord.getValue();
  }
}
