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
import io.zeebe.exporter.api.record.value.VariableRecordValue;
import java.util.stream.Stream;

public class VariableRecordStream
    extends ExporterRecordStream<VariableRecordValue, VariableRecordStream> {

  public VariableRecordStream(final Stream<Record<VariableRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected VariableRecordStream supply(final Stream<Record<VariableRecordValue>> wrappedStream) {
    return new VariableRecordStream(wrappedStream);
  }

  public VariableRecordStream withName(final String name) {
    return valueFilter(v -> v.getName().equals(name));
  }

  public VariableRecordStream withScopeKey(final long scopeKey) {
    return valueFilter(v -> v.getScopeKey() == scopeKey);
  }

  public VariableRecordStream withValue(final String value) {
    return valueFilter(v -> v.getValue().equals(value));
  }

  public VariableRecordStream withWorkflowInstanceKey(final long workflowInstanceKey) {
    return valueFilter(v -> v.getWorkflowInstanceKey() == workflowInstanceKey);
  }
}
