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
import java.time.OffsetDateTime;
import java.util.Objects;

public class JobStatusMetricImpl implements JobStatusMetric {

  private final long count;
  private final OffsetDateTime lastUpdatedAt;

  public JobStatusMetricImpl(final long count, final OffsetDateTime lastUpdatedAt) {
    this.count = count;
    this.lastUpdatedAt = lastUpdatedAt;
  }

  @Override
  public long getCount() {
    return count;
  }

  @Override
  public OffsetDateTime getLastUpdatedAt() {
    return lastUpdatedAt;
  }

  @Override
  public int hashCode() {
    return Objects.hash(count, lastUpdatedAt);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JobStatusMetricImpl that = (JobStatusMetricImpl) o;
    return count == that.count && Objects.equals(lastUpdatedAt, that.lastUpdatedAt);
  }

  @Override
  public String toString() {
    return "JobStatusMetricImpl{" + "count=" + count + ", lastUpdatedAt=" + lastUpdatedAt + '}';
  }
}
