/*
 * Zeebe Broker Core
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
package io.zeebe.broker.exporter.record.value;

import io.zeebe.broker.exporter.ExporterObjectMapper;
import io.zeebe.broker.exporter.record.RecordValueImpl;
import io.zeebe.exporter.api.record.value.JobBatchRecordValue;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class JobBatchRecordValueImpl extends RecordValueImpl implements JobBatchRecordValue {
  private final String type;
  private final String worker;
  private final Duration timeout;
  private final int maxJobsToActivate;
  private final List<Long> jobKeys;
  private final List<JobRecordValue> jobs;
  private final boolean truncated;

  public JobBatchRecordValueImpl(
      final ExporterObjectMapper objectMapper,
      final String type,
      final String worker,
      final Duration timeout,
      final int maxJobsToActivate,
      final List<Long> jobKeys,
      final List<JobRecordValue> jobs,
      boolean truncated) {
    super(objectMapper);
    this.type = type;
    this.worker = worker;
    this.timeout = timeout;
    this.maxJobsToActivate = maxJobsToActivate;
    this.jobKeys = jobKeys;
    this.jobs = jobs;
    this.truncated = truncated;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public String getWorker() {
    return worker;
  }

  @Override
  public Duration getTimeoutDuration() {
    return timeout;
  }

  @Override
  public int getMaxJobsToActivate() {
    return maxJobsToActivate;
  }

  @Override
  public List<Long> getJobKeys() {
    return jobKeys;
  }

  @Override
  public List<JobRecordValue> getJobs() {
    return jobs;
  }

  @Override
  public boolean isTruncated() {
    return truncated;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JobBatchRecordValueImpl that = (JobBatchRecordValueImpl) o;
    return maxJobsToActivate == that.maxJobsToActivate
        && Objects.equals(type, that.type)
        && Objects.equals(worker, that.worker)
        && Objects.equals(timeout, that.timeout)
        && Objects.equals(jobKeys, that.jobKeys)
        && Objects.equals(jobs, that.jobs)
        && truncated == that.truncated;
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, worker, timeout, maxJobsToActivate, jobKeys, jobs, truncated);
  }

  @Override
  public String toString() {
    return "JobBatchRecordValueImpl{"
        + "type='"
        + type
        + '\''
        + ", truncated="
        + truncated
        + ", worker='"
        + worker
        + '\''
        + ", timeout="
        + timeout
        + ", maxJobsToActivate="
        + maxJobsToActivate
        + ", jobKeys="
        + jobKeys
        + ", jobs="
        + jobs
        + '}';
  }
}
