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

import static io.zeebe.protocol.intent.JobIntent.COMPLETED;
import static io.zeebe.protocol.intent.JobIntent.FAILED;

import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.util.record.RecordingExporter;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class JobClient {

  private final JobRecord jobRecord;
  private final StreamProcessorRule environmentRule;
  private long workflowInstanceKey;

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

  public Record<JobRecordValue> complete() {
    final boolean hasSpecificType = !jobRecord.getType().isEmpty();

    final Record<JobRecordValue> createdJob =
        RecordingExporter.jobRecords()
            .valueFilter(v -> hasSpecificType ? v.getType().equals(jobRecord.getType()) : true)
            .withIntent(JobIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    return completeAndWait(createdJob.getKey());
  }

  public Record<JobRecordValue> completeAndWait(long jobKey) {
    final long position = complete(jobKey);
    return RecordingExporter.jobRecords()
        .withIntent(COMPLETED)
        .withSourceRecordPosition(position)
        .getFirst();
  }

  public long complete(long jobKey) {
    return environmentRule.writeCommand(jobKey, JobIntent.COMPLETE, jobRecord);
  }

  public Record<JobRecordValue> failAndWait() {
    final Record<JobRecordValue> createdJob =
        RecordingExporter.jobRecords()
            .withType(jobRecord.getType())
            .withIntent(JobIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    return failAndWait(createdJob.getKey());
  }

  public Record<JobRecordValue> failAndWait(long jobKey) {
    final long position = fail(jobKey);

    return RecordingExporter.jobRecords(FAILED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withSourceRecordPosition(position)
        .getFirst();
  }

  public long fail(long jobKey) {
    return environmentRule.writeCommand(jobKey, JobIntent.FAIL, jobRecord);
  }
}
