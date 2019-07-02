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
import io.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import io.zeebe.test.util.collection.Maps;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class VariableDocumentRecordStream
    extends ExporterRecordStream<VariableDocumentRecordValue, VariableDocumentRecordStream> {

  public VariableDocumentRecordStream(
      final Stream<Record<VariableDocumentRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected VariableDocumentRecordStream supply(
      final Stream<Record<VariableDocumentRecordValue>> wrappedStream) {
    return new VariableDocumentRecordStream(wrappedStream);
  }

  public VariableDocumentRecordStream withScopeKey(long scopeKey) {
    return valueFilter(v -> v.getScopeKey() == scopeKey);
  }

  public VariableDocumentRecordStream withUpdateSemantics(
      VariableDocumentUpdateSemantic updateSemantics) {
    return valueFilter(v -> v.getUpdateSemantics() == updateSemantics);
  }

  public VariableDocumentRecordStream withVariables(Map<String, Object> variables) {
    return valueFilter(v -> v.getVariables().equals(variables));
  }

  public VariableDocumentRecordStream withVariables(Map.Entry<String, Object>... entries) {
    return withVariables(Maps.of(entries));
  }

  public VariableDocumentRecordStream withVariables(
      Predicate<Map<String, Object>> variablesMatcher) {
    return valueFilter(v -> variablesMatcher.test(v.getVariables()));
  }
}
