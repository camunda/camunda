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
import io.zeebe.exporter.api.record.value.JobBatchRecordValue;
import java.time.Duration;
import java.util.stream.Stream;

public class JobBatchRecordStream
    extends ExporterRecordStream<JobBatchRecordValue, JobBatchRecordStream> {

  public JobBatchRecordStream(final Stream<Record<JobBatchRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected JobBatchRecordStream supply(final Stream<Record<JobBatchRecordValue>> wrappedStream) {
    return new JobBatchRecordStream(wrappedStream);
  }

  public JobBatchRecordStream withType(final String type) {
    return valueFilter(v -> type.equals(v.getType()));
  }

  public JobBatchRecordStream withWorker(final String worker) {
    return valueFilter(v -> worker.equals(v.getWorker()));
  }

  public JobBatchRecordStream withTimeout(final Duration timeout) {
    return valueFilter(v -> timeout.equals(v.getTimeout()));
  }

  public JobBatchRecordStream withTimeout(final long timeout) {
    return valueFilter(v -> Duration.ofMillis(timeout).equals(v.getTimeout()));
  }

  public JobBatchRecordStream withMaxJobsToActivate(final int maxJobsToActivate) {
    return valueFilter(v -> v.getMaxJobsToActivate() == maxJobsToActivate);
  }
}
