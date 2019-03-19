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
import io.zeebe.exporter.api.record.RecordValueWithVariables;
import java.util.Map;
import java.util.stream.Stream;

public abstract class ExporterRecordWithVariablesStream<
        T extends RecordValueWithVariables, S extends ExporterRecordWithVariablesStream<T, S>>
    extends ExporterRecordStream<T, S> {

  public ExporterRecordWithVariablesStream(final Stream<Record<T>> wrappedStream) {
    super(wrappedStream);
  }

  public S withVariables(final String variables) {
    return valueFilter(v -> variables.equals(v.getVariables()));
  }

  public S withVariables(final Map<String, Object> variables) {
    return valueFilter(v -> variables.equals(v.getVariablesAsMap()));
  }

  public S withVariablesContaining(final String key) {
    return valueFilter(v -> v.getVariablesAsMap().containsKey(key));
  }

  public S withVariablesContaining(final String key, final Object value) {
    return valueFilter(v -> value.equals(v.getVariablesAsMap().get(key)));
  }
}
