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
import java.util.Map;
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
   * @return true if the batch was truncated due to too many buckets (job type + tenant
   *     combinations)
   */
  boolean getHasTooManyBuckets();

  /**
   * @return true if any field limit was exceeded during export
   */
  boolean getHasFieldLimitExceeded();

  /**
   * @return the list of encoded strings for space-efficient storage. Job types, tenant IDs, and
   *     worker names are stored here and referenced by index in the metrics map.
   */
  List<String> getEncodedStrings();

  /**
   * @return a map from encoded key (jobTypeIndex_tenantIdIndex) to the job metrics value for that
   *     combination
   */
  Map<String, JobMetricsBatchValue> getMetricsByTypeAndTenant();

  /** Represents the metrics value for a specific job type and tenant combination. */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableJobMetricsBatchValue.Builder.class)
  interface JobMetricsBatchValue {

    /**
     * @return array of status metrics indexed by JobMetricState ordinal. Each element contains
     *     count and lastUpdatedAt for that state.
     */
    List<StatusMetricValue> getJobTypeCounters();

    /**
     * @return a map from encoded worker name index to that worker's status metrics
     */
    Map<String, List<StatusMetricValue>> getJobWorkerCounters();
  }

  /** Represents a single status metric with count and timestamp. */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableStatusMetricValue.Builder.class)
  interface StatusMetricValue {

    /**
     * @return the count of events for this status
     */
    int getCount();

    /**
     * @return the timestamp when this status was last updated (epoch milliseconds)
     */
    long getLastUpdatedAt();
  }
}
