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
import io.zeebe.broker.exporter.record.RecordValueWithVariablesImpl;
import io.zeebe.broker.exporter.record.value.job.HeadersImpl;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.exporter.api.record.value.job.Headers;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public class JobRecordValueImpl extends RecordValueWithVariablesImpl implements JobRecordValue {
  private final String type;
  private final String worker;
  private final Instant deadline;
  private final HeadersImpl headers;
  private final Map<String, Object> customHeaders;
  private final int retries;
  private final String errorMessage;

  public JobRecordValueImpl(
      final ExporterObjectMapper objectMapper,
      final String variables,
      final String type,
      final String worker,
      final Instant deadline,
      final HeadersImpl headers,
      final Map<String, Object> customHeaders,
      final int retries,
      final String errorMessage) {
    super(objectMapper, variables);
    this.type = type;
    this.worker = worker;
    this.deadline = deadline;
    this.headers = headers;
    this.customHeaders = customHeaders;
    this.retries = retries;
    this.errorMessage = errorMessage;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public Headers getHeaders() {
    return headers;
  }

  @Override
  public Map<String, Object> getCustomHeaders() {
    return customHeaders;
  }

  @Override
  public String getWorker() {
    return worker;
  }

  @Override
  public int getRetries() {
    return retries;
  }

  @Override
  public Instant getDeadline() {
    return deadline;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final JobRecordValueImpl that = (JobRecordValueImpl) o;
    return retries == that.retries
        && Objects.equals(type, that.type)
        && Objects.equals(worker, that.worker)
        && Objects.equals(deadline, that.deadline)
        && Objects.equals(headers, that.headers)
        && Objects.equals(customHeaders, that.customHeaders)
        && Objects.equals(errorMessage, that.errorMessage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(), type, worker, deadline, headers, customHeaders, retries, errorMessage);
  }

  @Override
  public String toString() {
    return "JobRecordValueImpl{"
        + "type='"
        + type
        + '\''
        + ", worker='"
        + worker
        + '\''
        + ", deadline="
        + deadline
        + ", headers="
        + headers
        + ", customHeaders="
        + customHeaders
        + ", retries="
        + retries
        + ", errorMessage='"
        + errorMessage
        + '\''
        + ", variables='"
        + variables
        + '\''
        + '}';
  }
}
