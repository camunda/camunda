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
import io.zeebe.exporter.record.RecordValueWithPayload;
import java.util.Map;
import java.util.stream.Stream;

public abstract class ExporterRecordWithPayloadStream<
        T extends RecordValueWithPayload, S extends ExporterRecordWithPayloadStream<T, S>>
    extends ExporterRecordStream<T, S> {

  public ExporterRecordWithPayloadStream(final Stream<Record<T>> wrappedStream) {
    super(wrappedStream);
  }

  public S withPayload(final String payload) {
    return valueFilter(v -> payload.equals(v.getPayload()));
  }

  public S withPayload(final Map<String, Object> payload) {
    return valueFilter(v -> payload.equals(v.getPayloadAsMap()));
  }

  public S withPayloadContaining(final String key) {
    return valueFilter(v -> v.getPayloadAsMap().containsKey(key));
  }

  public S withPayloadContaining(final String key, final Object value) {
    return valueFilter(v -> value.equals(v.getPayloadAsMap().get(key)));
  }
}
