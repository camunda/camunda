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

import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.JobBatchIntent;
import io.zeebe.protocol.record.intent.JobIntent;

public final class RecordToWrite {

  private static final int DEFAULT_KEY = 1;
  private final RecordMetadata recordMetadata;
  private UnifiedRecordValue unifiedRecordValue;
  private int sourceIndex = -1;

  public static RecordToWrite command() {
    final RecordMetadata recordMetadata = new RecordMetadata();
    return new RecordToWrite(recordMetadata.recordType(RecordType.COMMAND));
  }

  public static RecordToWrite event() {
    final RecordMetadata recordMetadata = new RecordMetadata();
    return new RecordToWrite(recordMetadata.recordType(RecordType.EVENT));
  }

  private RecordToWrite(RecordMetadata recordMetadata) {
    this.recordMetadata = recordMetadata;
  }

  public RecordToWrite job(JobIntent intent) {
    recordMetadata.valueType(ValueType.JOB).intent(intent);

    final JobRecord jobRecord = new JobRecord();
    jobRecord.setType("type").setRetries(3).setWorker("worker");

    unifiedRecordValue = jobRecord;
    return this;
  }

  public RecordToWrite jobBatch(JobBatchIntent intent) {
    recordMetadata.valueType(ValueType.JOB_BATCH).intent(intent);

    final JobBatchRecord jobBatchRecord =
        new JobBatchRecord()
            .setWorker("worker")
            .setTimeout(10_000L)
            .setType("type")
            .setMaxJobsToActivate(1);

    unifiedRecordValue = jobBatchRecord;
    return this;
  }

  public RecordToWrite causedBy(int index) {
    sourceIndex = index;
    return this;
  }

  public long getKey() {
    return DEFAULT_KEY;
  }

  public RecordMetadata getRecordMetadata() {
    return recordMetadata;
  }

  public UnifiedRecordValue getUnifiedRecordValue() {
    return unifiedRecordValue;
  }

  public int getSourceIndex() {
    return sourceIndex;
  }
}
