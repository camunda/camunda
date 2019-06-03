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
import io.zeebe.exporter.api.record.value.JobBatchRecordValue;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.intent.JobBatchIntent;
import io.zeebe.test.util.record.RecordingExporter;

public class JobActivationClient {
  private static final int DEFAULT_PARTITION = 1;
  private static final long DEFAULT_TIMEOUT = 10000L;
  private static final String DEFAULT_WORKER = "defaultWorker";
  private static final int DEFAULT_MAX_ACTIVATE = 10;

  private final StreamProcessorRule environmentRule;
  private final JobBatchRecord jobBatchRecord;

  private int partitionId;

  public JobActivationClient(StreamProcessorRule environmentRule) {
    this.environmentRule = environmentRule;

    this.jobBatchRecord = new JobBatchRecord();
    jobBatchRecord
        .setTimeout(DEFAULT_TIMEOUT)
        .setWorker(DEFAULT_WORKER)
        .setMaxJobsToActivate(DEFAULT_MAX_ACTIVATE);
    partitionId = DEFAULT_PARTITION;
  }

  public JobActivationClient withType(String type) {
    jobBatchRecord.setType(type);
    return this;
  }

  public JobActivationClient withTimeout(long timeout) {
    jobBatchRecord.setTimeout(timeout);

    return this;
  }

  public JobActivationClient byWorker(String name) {
    jobBatchRecord.setWorker(name);
    return this;
  }

  public JobActivationClient onPartition(int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public JobActivationClient withMaxJobsToActivate(int count) {
    jobBatchRecord.setMaxJobsToActivate(count);
    return this;
  }

  public Record<JobBatchRecordValue> activateAndWait() {
    final long position = activate();
    return RecordingExporter.jobBatchRecords()
        .withIntent(JobBatchIntent.ACTIVATED)
        .withSourceRecordPosition(position)
        .getFirst();
  }

  public long activate() {
    return environmentRule.writeCommandOnPartition(
        partitionId, JobBatchIntent.ACTIVATE, jobBatchRecord);
  }
}
