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
package io.camunda.zeebe.protocol.record.value;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import java.util.List;
import org.immutables.value.Value;

/**
 * Represents a job batch related event or command.
 *
 * <p>See {@link JobBatchIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableJobBatchRecordValue.Builder.class)
public interface JobBatchRecordValue extends RecordValue {
  /**
   * @return the type of the job
   */
  String getType();

  /**
   * @return the assigned worker to complete the job
   */
  String getWorker();

  /**
   * @return the timeout (time span in milliseconds) for which a job is exclusively assigned to this
   *     worker. If the timeout is exceeded, it can happen that the job is handed to another worker
   *     and the work is performed twice.
   */
  long getTimeout();

  /**
   * @return the number of jobs to handle
   */
  int getMaxJobsToActivate();

  /**
   * @return list of the keys from the jobs assigned to this batch
   */
  List<Long> getJobKeys();

  /**
   * @return the jobs assigned to this batch
   */
  List<JobRecordValue> getJobs();

  /**
   * @return the broker has more JobRecords that couldn't fit in this batch
   */
  boolean isTruncated();

  /**
   * Since a job batch contains many jobs, it is possible that the jobs belong to different tenants.
   *
   * <p>This can be useful when requesting jobs for multiple tenants at once. Each of the activated
   * jobs will be owned by the tenant that owns the corresponding process instance.
   *
   * @return the identifiers of the tenants that this job batch may contain jobs for
   */
  List<String> getTenantIds();

  /**
   * @return the tenant filtering strategy used for job activation
   */
  TenantFilter getTenantFilter();
}
