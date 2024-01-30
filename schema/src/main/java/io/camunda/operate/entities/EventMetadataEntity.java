/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

public class EventMetadataEntity {

  /**
  * Job data.
  */
  private String jobType;
  private Integer jobRetries;
  private String jobWorker;
  private OffsetDateTime jobDeadline;
  private Map<String, String> jobCustomHeaders;
  private Long jobKey;

  /**
  * Incident data.
  */
  private ErrorType incidentErrorType;
  private String incidentErrorMessage;

  /**
   * Message data.
   */
  private String messageName;
  private String correlationKey;

  public String getJobType() {
    return jobType;
  }

  public void setJobType(String jobType) {
    this.jobType = jobType;
  }

  public Integer getJobRetries() {
    return jobRetries;
  }

  public void setJobRetries(Integer jobRetries) {
    this.jobRetries = jobRetries;
  }

  public String getJobWorker() {
    return jobWorker;
  }

  public void setJobWorker(String jobWorker) {
    this.jobWorker = jobWorker;
  }

  public OffsetDateTime getJobDeadline() {
    return jobDeadline;
  }

  public void setJobDeadline(OffsetDateTime jobDeadline) {
    this.jobDeadline = jobDeadline;
  }

  public Map<String, String> getJobCustomHeaders() {
    return jobCustomHeaders;
  }

  public void setJobCustomHeaders(Map<String, String> jobCustomHeaders) {
    this.jobCustomHeaders = jobCustomHeaders;
  }

  public Long getJobKey() {
    return jobKey;
  }

  public void setJobKey(Long jobKey) {
    this.jobKey = jobKey;
  }

  public ErrorType getIncidentErrorType() {
    return incidentErrorType;
  }

  public void setIncidentErrorType(ErrorType incidentErrorType) {
    this.incidentErrorType = incidentErrorType;
  }

  public String getIncidentErrorMessage() {
    return incidentErrorMessage;
  }

  public void setIncidentErrorMessage(String incidentErrorMessage) {
    this.incidentErrorMessage = incidentErrorMessage;
  }

  public String getMessageName() {
    return messageName;
  }

  public EventMetadataEntity setMessageName(final String messageName) {
    this.messageName = messageName;
    return this;
  }

  public String getCorrelationKey() {
    return correlationKey;
  }

  public EventMetadataEntity setCorrelationKey(final String correlationKey) {
    this.correlationKey = correlationKey;
    return this;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EventMetadataEntity that = (EventMetadataEntity) o;
    return Objects.equals(jobType, that.jobType) &&
        Objects.equals(jobRetries, that.jobRetries) &&
        Objects.equals(jobWorker, that.jobWorker) &&
        Objects.equals(jobDeadline, that.jobDeadline) &&
        Objects.equals(jobCustomHeaders, that.jobCustomHeaders) &&
        Objects.equals(jobKey, that.jobKey) &&
        incidentErrorType == that.incidentErrorType &&
        Objects.equals(incidentErrorMessage, that.incidentErrorMessage) &&
        Objects.equals(messageName, that.messageName) &&
        Objects.equals(correlationKey, that.correlationKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jobType, jobRetries, jobWorker, jobDeadline, jobCustomHeaders, jobKey,
        incidentErrorType, incidentErrorMessage, messageName, correlationKey);
  }
}
