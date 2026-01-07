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
import java.util.List;
import org.immutables.value.Value;

/**
 * Represents a batch export of job worker metrics. Contains aggregated job state counters for job
 * types and workers within a time window.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableJobMetricsBatchRecordValue.Builder.class)
public interface JobMetricsBatchRecordValue extends RecordValue {

  /**
   * @return the start timestamp of the batch window (epoch milliseconds)
   */
  long getBatchStartTime();

  /**
   * @return the end timestamp of the batch window (epoch milliseconds)
   */
  long getBatchEndTime();

  /**
   * @return true if the record size limit was exceeded during export
   */
  boolean getRecordSizeLimitExceeded();

  /**
   * @return the list of encoded strings for space-efficient storage. Job types, tenant IDs, and
   *     worker names are stored here and referenced by index in the metrics.
   */
  List<String> getEncodedStrings();

  /**
   * @return a list of job metrics values, each containing metrics for a specific combination of job
   *     type, tenant ID, and worker name. The indices reference strings stored in {@link
   *     #getEncodedStrings()}.
   */
  List<JobMetricsValue> getJobMetrics();

  /**
   * Represents job metrics for a specific combination of job type, tenant ID, and worker name. Uses
   * indices to reference encoded strings for space-efficient storage.
   */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableJobMetricsValue.Builder.class)
  interface JobMetricsValue {

    /**
     * @return the index into {@link #getEncodedStrings()} for the job type name
     */
    int getJobTypeIndex();

    /**
     * @return the index into {@link #getEncodedStrings()} for the tenant ID
     */
    int getTenantIdIndex();

    /**
     * @return the index into {@link #getEncodedStrings()} for the worker name
     */
    int getWorkerNameIndex();

    /**
     * @return the list of status metrics (e.g., ACTIVATED, COMPLETED, FAILED) and their counts for
     *     this job type, tenant, and worker combination The index in the list corresponds to the
     *     job status ordinal value.
     */
    List<StatusMetricValue> getStatusMetrics();
  }

  /**
   * Represents an aggregated job status metric within the batch time window. Contains the count of
   * events for a specific job status and the timestamp when this metric was last updated.
   */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableStatusMetricValue.Builder.class)
  interface StatusMetricValue {

    /**
     * @return the count of job events for this status (e.g., ACTIVATED, COMPLETED, FAILED,
     *     TIMED_OUT) within the batch window
     */
    int getCount();

    /**
     * @return the timestamp when this status metric was last updated (epoch milliseconds),
     *     reflecting the latest event time for this status within the batch window
     */
    long getLastUpdatedAt();
  }
}
