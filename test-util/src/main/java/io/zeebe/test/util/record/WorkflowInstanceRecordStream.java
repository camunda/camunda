/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.test.util.record;

import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.stream.Stream;

public class WorkflowInstanceRecordStream
    extends ExporterRecordStream<WorkflowInstanceRecordValue, WorkflowInstanceRecordStream> {

  public WorkflowInstanceRecordStream(final Stream<Record<WorkflowInstanceRecordValue>> stream) {
    super(stream);
  }

  @Override
  protected WorkflowInstanceRecordStream supply(
      final Stream<Record<WorkflowInstanceRecordValue>> stream) {
    return new WorkflowInstanceRecordStream(stream);
  }

  public WorkflowInstanceRecordStream withBpmnProcessId(final String bpmnProcessId) {
    return valueFilter(v -> bpmnProcessId.equals(v.getBpmnProcessId()));
  }

  public WorkflowInstanceRecordStream withVersion(final int version) {
    return valueFilter(v -> v.getVersion() == version);
  }

  public WorkflowInstanceRecordStream withWorkflowKey(final long workflowKey) {
    return valueFilter(v -> v.getWorkflowKey() == workflowKey);
  }

  public WorkflowInstanceRecordStream withWorkflowInstanceKey(final long workflowInstanceKey) {
    return valueFilter(v -> v.getWorkflowInstanceKey() == workflowInstanceKey);
  }

  public WorkflowInstanceRecordStream withElementId(final String elementId) {
    return valueFilter(v -> elementId.equals(v.getElementId()));
  }

  public WorkflowInstanceRecordStream withFlowScopeKey(final long flowScopeKey) {
    return valueFilter(v -> v.getFlowScopeKey() == flowScopeKey);
  }

  public WorkflowInstanceRecordStream limitToWorkflowInstanceCompleted() {
    return limit(
        r ->
            r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_COMPLETED
                && r.getKey() == r.getValue().getWorkflowInstanceKey());
  }

  public WorkflowInstanceRecordStream withElementType(BpmnElementType elementType) {
    return valueFilter(v -> v.getBpmnElementType() == elementType);
  }

  /**
   * @return stream with only records for the workflow instance (i.e. root scope of the instance)
   */
  public WorkflowInstanceRecordStream filterRootScope() {
    return filter(r -> r.getKey() == r.getValue().getWorkflowInstanceKey());
  }
}
