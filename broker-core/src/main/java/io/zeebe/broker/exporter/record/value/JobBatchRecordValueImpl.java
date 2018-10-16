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
import io.zeebe.exporter.record.value.JobBatchRecordValue;
import io.zeebe.exporter.record.value.JobRecordValue;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class JobBatchRecordValueImpl extends RecordValueImpl implements JobBatchRecordValue {
  private final String type;
  private final String worker;
  private final Duration timeout;
  private final int amount;
  private final List<Long> jobKeys;
  private final List<JobRecordValue> jobs;

  public JobBatchRecordValueImpl(
      final ExporterObjectMapper objectMapper,
      final String type,
      final String worker,
      final Duration timeout,
      final int amount,
      final List<Long> jobKeys,
      final List<JobRecordValue> jobs) {
    super(objectMapper);
    this.type = type;
    this.worker = worker;
    this.timeout = timeout;
    this.amount = amount;
    this.jobKeys = jobKeys;
    this.jobs = jobs;
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
  public Duration getTimeout() {
    return timeout;
  }

  @Override
  public int getAmount() {
    return amount;
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
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JobBatchRecordValueImpl that = (JobBatchRecordValueImpl) o;
    return amount == that.amount
        && Objects.equals(type, that.type)
        && Objects.equals(worker, that.worker)
        && Objects.equals(timeout, that.timeout)
        && Objects.equals(jobKeys, that.jobKeys)
        && Objects.equals(jobs, that.jobs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, worker, timeout, amount, jobKeys, jobs);
  }

  @Override
  public String toString() {
    return "JobBatchRecordValueImpl{"
        + "type='"
        + type
        + '\''
        + ", worker='"
        + worker
        + '\''
        + ", timeout="
        + timeout
        + ", amount="
        + amount
        + ", jobKeys="
        + jobKeys
        + ", jobs="
        + jobs
        + '}';
  }
}
