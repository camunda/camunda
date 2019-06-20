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
import io.zeebe.protocol.record.value.JobRecordValue;
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

  public JobRecordStream withRetries(final int retries) {
    return valueFilter(v -> v.getRetries() == retries);
  }

  public JobRecordStream withWorkflowInstanceKey(long workflowInstanceKey) {
    return valueFilter(v -> v.getWorkflowInstanceKey() == workflowInstanceKey);
  }
}
