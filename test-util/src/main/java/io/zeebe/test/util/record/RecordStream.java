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
import io.zeebe.exporter.api.record.RecordValue;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.function.Predicate;
import java.util.stream.Stream;

@SuppressWarnings("unchecked")
public class RecordStream extends ExporterRecordStream<RecordValue, RecordStream> {
  public RecordStream(Stream<Record<RecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected RecordStream supply(Stream<Record<RecordValue>> wrappedStream) {
    return new RecordStream(wrappedStream);
  }

  public RecordStream between(long lowerBoundPosition, long upperBoundPosition) {
    return between(
        r -> r.getPosition() > lowerBoundPosition, r -> r.getPosition() >= upperBoundPosition);
  }

  public RecordStream between(Record<?> lowerBound, Record<?> upperBound) {
    return between(lowerBound::equals, upperBound::equals);
  }

  public RecordStream between(Predicate<Record<?>> lowerBound, Predicate<Record<?>> upperBound) {
    return limit(upperBound::test).skipUntil(lowerBound::test);
  }

  public RecordStream limitToWorkflowInstance(long workflowInstanceKey) {
    return between(
        r ->
            r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_ACTIVATING
                && r.getKey() == workflowInstanceKey,
        r ->
            r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_COMPLETED
                && r.getKey() == workflowInstanceKey);
  }

  public WorkflowInstanceRecordStream workflowInstanceRecords() {
    return new WorkflowInstanceRecordStream(
        filter(r -> r.getMetadata().getValueType() == ValueType.WORKFLOW_INSTANCE)
            .map(Record.class::cast));
  }

  public TimerRecordStream timerRecords() {
    return new TimerRecordStream(
        filter(r -> r.getMetadata().getValueType() == ValueType.TIMER).map(Record.class::cast));
  }

  public VariableDocumentRecordStream variableDocumentRecords() {
    return new VariableDocumentRecordStream(
        filter(r -> r.getMetadata().getValueType() == ValueType.VARIABLE_DOCUMENT)
            .map(Record.class::cast));
  }

  public VariableRecordStream variableRecords() {
    return new VariableRecordStream(
        filter(r -> r.getMetadata().getValueType() == ValueType.VARIABLE).map(Record.class::cast));
  }
}
