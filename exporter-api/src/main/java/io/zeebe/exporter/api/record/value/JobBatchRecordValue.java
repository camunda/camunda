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
package io.zeebe.exporter.api.record.value;

import io.zeebe.exporter.api.record.RecordValue;
import java.time.Duration;
import java.util.List;

/**
 * Represents a job batch related event or command.
 *
 * <p>See {@link io.zeebe.protocol.intent.JobBatchIntent} for intents.
 */
public interface JobBatchRecordValue extends RecordValue {
  /** @return the type of the job */
  String getType();

  /** @return the assigned worker to complete the job */
  String getWorker();

  /**
   * @return the timeout when the job is exclusively assigned to this worker. If the timeout is
   *     exceeded, it can happen that the job is handed to another worker and the work is performed
   *     twice.
   */
  Duration getTimeout();

  /** @return the number of jobs to handle */
  int getMaxJobsToActivate();

  /** @return list of the keys from the jobs assigned to this batch */
  List<Long> getJobKeys();

  /** @return the jobs assigned to this batch */
  List<JobRecordValue> getJobs();

  /** @return the broker has more JobRecords that couldn't fit in this batch */
  boolean isTruncated();
}
