/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.util;

import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class JobClient {
  private static final Function<Long, Record<JobRecordValue>> SUCCESS_SUPPLIER =
      (position) -> RecordingExporter.jobRecords().withSourceRecordPosition(position).getFirst();

  private static final Function<Long, Record<JobRecordValue>> REJECTION_SUPPLIER =
      (position) ->
          RecordingExporter.jobRecords()
              .onlyCommandRejections()
              .withSourceRecordPosition(position)
              .getFirst();

  private final JobRecord jobRecord;
  private final StreamProcessorRule environmentRule;
  private long workflowInstanceKey;

  private Function<Long, Record<JobRecordValue>> expectation = SUCCESS_SUPPLIER;

  public JobClient(StreamProcessorRule environmentRule) {
    this.environmentRule = environmentRule;
    this.jobRecord = new JobRecord();
  }

  public JobClient ofInstance(long workflowInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
    return this;
  }

  public JobClient withVariables(String variables) {
    jobRecord.setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(variables)));
    return this;
  }

  public JobClient withVariables(DirectBuffer variables) {
    jobRecord.setVariables(variables);
    return this;
  }

  public JobClient withType(String jobType) {
    jobRecord.setType(jobType);
    return this;
  }

  public JobClient withRetries(int retries) {
    jobRecord.setRetries(retries);
    return this;
  }

  public JobClient withErrorMessage(String message) {
    jobRecord.setErrorMessage(message);
    return this;
  }

  public JobClient expectRejection() {
    expectation = REJECTION_SUPPLIER;
    return this;
  }

  public Record<JobRecordValue> complete() {
    final boolean hasSpecificType = !jobRecord.getType().isEmpty();

    final Record<JobRecordValue> createdJob =
        RecordingExporter.jobRecords()
            .valueFilter(v -> hasSpecificType ? v.getType().equals(jobRecord.getType()) : true)
            .withIntent(JobIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    return complete(createdJob.getKey());
  }

  public Record<JobRecordValue> complete(long jobKey) {
    final long position = environmentRule.writeCommand(jobKey, JobIntent.COMPLETE, jobRecord);
    return expectation.apply(position);
  }

  public Record<JobRecordValue> fail(long jobKey) {
    final long position = environmentRule.writeCommand(jobKey, JobIntent.FAIL, jobRecord);
    return expectation.apply(position);
  }

  public Record<JobRecordValue> updateRetries(long jobKey) {
    final long position = environmentRule.writeCommand(jobKey, JobIntent.UPDATE_RETRIES, jobRecord);
    return expectation.apply(position);
  }
}
