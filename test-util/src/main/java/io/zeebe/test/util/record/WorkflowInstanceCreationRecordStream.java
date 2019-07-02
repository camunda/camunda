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

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.record.value.WorkflowInstanceCreationRecordValue;
import io.zeebe.test.util.collection.Maps;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class WorkflowInstanceCreationRecordStream
    extends ExporterRecordStream<
        WorkflowInstanceCreationRecordValue, WorkflowInstanceCreationRecordStream> {

  public WorkflowInstanceCreationRecordStream(
      final Stream<Record<WorkflowInstanceCreationRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected WorkflowInstanceCreationRecordStream supply(
      final Stream<Record<WorkflowInstanceCreationRecordValue>> wrappedStream) {
    return new WorkflowInstanceCreationRecordStream(wrappedStream);
  }

  public WorkflowInstanceCreationRecordStream withBpmnProcessId(String bpmnProcessId) {
    return valueFilter(v -> v.getBpmnProcessId().equals(bpmnProcessId));
  }

  public WorkflowInstanceCreationRecordStream withVersion(int version) {
    return valueFilter(v -> v.getVersion() == version);
  }

  public WorkflowInstanceCreationRecordStream withKey(long key) {
    return valueFilter(v -> v.getWorkflowKey() == key);
  }

  public WorkflowInstanceCreationRecordStream withInstanceKey(long instanceKey) {
    return valueFilter(v -> v.getWorkflowInstanceKey() == instanceKey);
  }

  public WorkflowInstanceCreationRecordStream withVariables(Map<String, Object> variables) {
    return valueFilter(v -> v.getVariables().equals(variables));
  }

  public WorkflowInstanceCreationRecordStream withVariables(Map.Entry<String, Object>... entries) {
    return withVariables(Maps.of(entries));
  }

  public WorkflowInstanceCreationRecordStream withVariables(
      Predicate<Map<String, Object>> matcher) {
    return valueFilter(v -> matcher.test(v.getVariables()));
  }

  public WorkflowInstanceCreationRecordStream limitToWorkflowInstanceCreated(
      long workflowInstanceKey) {
    return limit(
        r ->
            r.getIntent() == WorkflowInstanceCreationIntent.CREATED
                && r.getValue().getWorkflowInstanceKey() == workflowInstanceKey);
  }
}
