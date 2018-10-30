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

import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.IncidentRecordValue;
import java.util.stream.Stream;

public class IncidentRecordStream
    extends ExporterRecordWithPayloadStream<IncidentRecordValue, IncidentRecordStream> {

  public IncidentRecordStream(final Stream<Record<IncidentRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected IncidentRecordStream supply(final Stream<Record<IncidentRecordValue>> wrappedStream) {
    return new IncidentRecordStream(wrappedStream);
  }

  public IncidentRecordStream withErrorType(final String errorType) {
    return valueFilter(v -> errorType.equals(v.getErrorType()));
  }

  public IncidentRecordStream withErrorMessage(final String errorMessage) {
    return valueFilter(v -> errorMessage.equals(v.getErrorMessage()));
  }

  public IncidentRecordStream withBpmnProcessId(final String bpmnProcessId) {
    return valueFilter(v -> bpmnProcessId.equals(v.getBpmnProcessId()));
  }

  public IncidentRecordStream withWorkflowInstanceKey(final long workflowInstanceKey) {
    return valueFilter(v -> v.getWorkflowInstanceKey() == workflowInstanceKey);
  }

  public IncidentRecordStream withJobKey(final long jobKey) {
    return valueFilter(v -> v.getJobKey() == jobKey);
  }
}
