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
package org.camunda.operate.zeebeimport.record.value;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.camunda.operate.zeebeimport.record.RecordValueWithPayloadImpl;
import org.camunda.operate.zeebeimport.record.value.job.HeadersImpl;
import io.zeebe.exporter.record.value.JobRecordValue;
import io.zeebe.exporter.record.value.job.Headers;

public class JobRecordValueImpl extends RecordValueWithPayloadImpl implements JobRecordValue {
  private String type;
  private String worker;
  private Instant deadline;
  private HeadersImpl headers;
  private Map<String, Object> customHeaders;
  private int retries;

  public JobRecordValueImpl() {
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

  public void setType(String type) {
    this.type = type;
  }

  public void setWorker(String worker) {
    this.worker = worker;
  }

  public void setDeadline(Instant deadline) {
    this.deadline = deadline;
  }

  public void setHeaders(HeadersImpl headers) {
    this.headers = headers;
  }

  public void setCustomHeaders(Map<String, Object> customHeaders) {
    this.customHeaders = customHeaders;
  }

  public void setRetries(int retries) {
    this.retries = retries;
  }

  @Override
  public boolean equals(final Object o) {
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
        && Objects.equals(customHeaders, that.customHeaders);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), type, worker, deadline, headers, customHeaders, retries);
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
        + ", payload='"
        + getPayload()
        + '\''
        + '}';
  }
}
