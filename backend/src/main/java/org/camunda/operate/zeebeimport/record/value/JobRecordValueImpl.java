/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.record.value;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.camunda.operate.zeebeimport.record.RecordValueWithPayloadImpl;
import org.camunda.operate.zeebeimport.record.value.job.HeadersImpl;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.protocol.record.value.job.Headers;

public class JobRecordValueImpl extends RecordValueWithPayloadImpl implements JobRecordValue {
  private String type;
  private String worker;
  private Instant deadline;
  private HeadersImpl headers;
  private Map<String, Object> customHeaders;
  private int retries;
  private String errorMessage;

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

  @Override
  public String getErrorMessage() {
    return errorMessage;
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

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
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
        + ", variables='"
        + getVariables()
        + '\''
        + '}';
  }
}
