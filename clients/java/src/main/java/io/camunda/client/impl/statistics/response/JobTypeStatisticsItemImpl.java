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
package io.camunda.client.impl.statistics.response;

import io.camunda.client.api.statistics.response.JobStatusMetric;
import io.camunda.client.api.statistics.response.JobTypeStatisticsItem;
import java.util.Objects;

public class JobTypeStatisticsItemImpl implements JobTypeStatisticsItem {

  private final String jobType;
  private final JobStatusMetric created;
  private final JobStatusMetric completed;
  private final JobStatusMetric failed;
  private final int workers;

  public JobTypeStatisticsItemImpl(
      final String jobType,
      final JobStatusMetric created,
      final JobStatusMetric completed,
      final JobStatusMetric failed,
      final int workers) {
    this.jobType = jobType;
    this.created = created;
    this.completed = completed;
    this.failed = failed;
    this.workers = workers;
  }

  @Override
  public String getJobType() {
    return jobType;
  }

  @Override
  public JobStatusMetric getCreated() {
    return created;
  }

  @Override
  public JobStatusMetric getCompleted() {
    return completed;
  }

  @Override
  public JobStatusMetric getFailed() {
    return failed;
  }

  @Override
  public int getWorkers() {
    return workers;
  }

  @Override
  public int hashCode() {
    return Objects.hash(jobType, created, completed, failed, workers);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JobTypeStatisticsItemImpl that = (JobTypeStatisticsItemImpl) o;
    return workers == that.workers
        && Objects.equals(jobType, that.jobType)
        && Objects.equals(created, that.created)
        && Objects.equals(completed, that.completed)
        && Objects.equals(failed, that.failed);
  }

  @Override
  public String toString() {
    return "JobTypeStatisticsItemImpl{"
        + "jobType='"
        + jobType
        + '\''
        + ", created="
        + created
        + ", completed="
        + completed
        + ", failed="
        + failed
        + ", workers="
        + workers
        + '}';
  }
}

