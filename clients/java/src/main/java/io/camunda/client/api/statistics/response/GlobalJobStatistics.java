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
package io.camunda.client.api.statistics.response;

/** Response for global job statistics query. */
public interface GlobalJobStatistics {

  /**
   * Returns the metric for jobs in CREATED state.
   *
   * @return the created jobs metric
   */
  JobStatusMetric getCreated();

  /**
   * Returns the metric for jobs in COMPLETED state.
   *
   * @return the completed jobs metric
   */
  JobStatusMetric getCompleted();

  /**
   * Returns the metric for jobs in FAILED state.
   *
   * @return the failed jobs metric
   */
  JobStatusMetric getFailed();

  /**
   * Returns whether the statistics are incomplete due to internal limits being reached.
   *
   * @return true if some metrics were not recorded due to limits
   */
  boolean isIncomplete();
}
