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
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.exporter.api.record.value.job.Headers;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

public class JobRecordStream
    extends ExporterRecordWithVariablesStream<JobRecordValue, JobRecordStream> {

  public JobRecordStream(final Stream<Record<JobRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected JobRecordStream supply(final Stream<Record<JobRecordValue>> wrappedStream) {
    return new JobRecordStream(wrappedStream);
  }

  public JobRecordStream withType(final String type) {
    return valueFilter(v -> type.equals(v.getType()));
  }

  public JobRecordStream withHeaders(final Headers headers) {
    return valueFilter(v -> headers.equals(v.getHeaders()));
  }

  public JobRecordStream withCustomHeaders(final Map<String, Object> customHeaders) {
    return valueFilter(v -> customHeaders.equals(v.getCustomHeaders()));
  }

  public JobRecordStream withCustomHeader(final String key, final Object value) {
    return valueFilter(v -> value.equals(v.getCustomHeaders().get(key)));
  }

  public JobRecordStream withWorker(final String worker) {
    return valueFilter(v -> worker.equals(v.getWorker()));
  }

  public JobRecordStream withRetries(final int retries) {
    return valueFilter(v -> v.getRetries() == retries);
  }

  public JobRecordStream withDeadline(final Instant deadline) {
    return valueFilter(v -> deadline.equals(v.getDeadline()));
  }

  public JobRecordStream withDeadline(final long deadline) {
    return valueFilter(v -> Instant.ofEpochMilli(deadline).equals(v.getDeadline()));
  }

  public JobRecordStream withWorkflowInstanceKey(long workflowInstanceKey) {
    return valueFilter(v -> v.getHeaders().getWorkflowInstanceKey() == workflowInstanceKey);
  }
}
