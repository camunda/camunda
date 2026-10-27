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
import io.camunda.client.api.statistics.response.JobTimeSeriesStatisticsItem;
import java.time.OffsetDateTime;
import java.util.Objects;

public class JobTimeSeriesStatisticsItemImpl implements JobTimeSeriesStatisticsItem {

  private final OffsetDateTime time;
  private final JobStatusMetric created;
  private final JobStatusMetric completed;
  private final JobStatusMetric failed;

  public JobTimeSeriesStatisticsItemImpl(
      final OffsetDateTime time,
      final JobStatusMetric created,
      final JobStatusMetric completed,
      final JobStatusMetric failed) {
    this.time = time;
    this.created = created;
    this.completed = completed;
    this.failed = failed;
  }

  @Override
  public OffsetDateTime getTime() {
    return time;
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
  public int hashCode() {
    return Objects.hash(time, created, completed, failed);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JobTimeSeriesStatisticsItemImpl that = (JobTimeSeriesStatisticsItemImpl) o;
    return Objects.equals(time, that.time)
        && Objects.equals(created, that.created)
        && Objects.equals(completed, that.completed)
        && Objects.equals(failed, that.failed);
  }

  @Override
  public String toString() {
    return "JobTimeSeriesStatisticsItemImpl{"
        + "time="
        + time
        + ", created="
        + created
        + ", completed="
        + completed
        + ", failed="
        + failed
        + '}';
  }
}
