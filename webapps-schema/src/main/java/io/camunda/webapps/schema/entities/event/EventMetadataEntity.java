/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.event;

import io.camunda.webapps.schema.entities.incident.ErrorType;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

public class EventMetadataEntity {

  /** Job data. */
  private String jobType;

  private Integer jobRetries;
  private String jobWorker;
  private OffsetDateTime jobDeadline;
  private Map<String, String> jobCustomHeaders;
  private Long jobKey;

  /** Incident data. */
  private ErrorType incidentErrorType;

  private String incidentErrorMessage;

  /** Message data. */
  private String messageName;

  private String correlationKey;

  public String getJobType() {
    return jobType;
  }

  public EventMetadataEntity setJobType(final String jobType) {
    this.jobType = jobType;
    return this;
  }

  public Integer getJobRetries() {
    return jobRetries;
  }

  public EventMetadataEntity setJobRetries(final Integer jobRetries) {
    this.jobRetries = jobRetries;
    return this;
  }

  public String getJobWorker() {
    return jobWorker;
  }

  public EventMetadataEntity setJobWorker(final String jobWorker) {
    this.jobWorker = jobWorker;
    return this;
  }

  public OffsetDateTime getJobDeadline() {
    return jobDeadline;
  }

  public EventMetadataEntity setJobDeadline(final OffsetDateTime jobDeadline) {
    this.jobDeadline = jobDeadline;
    return this;
  }

  public Map<String, String> getJobCustomHeaders() {
    return jobCustomHeaders;
  }

  public EventMetadataEntity setJobCustomHeaders(final Map<String, String> jobCustomHeaders) {
    this.jobCustomHeaders = jobCustomHeaders;
    return this;
  }

  public Long getJobKey() {
    return jobKey;
  }

  public void setJobKey(final Long jobKey) {
    this.jobKey = jobKey;
  }

  public ErrorType getIncidentErrorType() {
    return incidentErrorType;
  }

  public void setIncidentErrorType(final ErrorType incidentErrorType) {
    this.incidentErrorType = incidentErrorType;
  }

  public String getIncidentErrorMessage() {
    return incidentErrorMessage;
  }

  public void setIncidentErrorMessage(final String incidentErrorMessage) {
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
  public int hashCode() {
    return Objects.hash(
        jobType,
        jobRetries,
        jobWorker,
        jobDeadline,
        jobCustomHeaders,
        jobKey,
        incidentErrorType,
        incidentErrorMessage,
        messageName,
        correlationKey);
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
    return Objects.equals(jobType, that.jobType)
        && Objects.equals(jobRetries, that.jobRetries)
        && Objects.equals(jobWorker, that.jobWorker)
        && Objects.equals(jobDeadline, that.jobDeadline)
        && Objects.equals(jobCustomHeaders, that.jobCustomHeaders)
        && Objects.equals(jobKey, that.jobKey)
        && incidentErrorType == that.incidentErrorType
        && Objects.equals(incidentErrorMessage, that.incidentErrorMessage)
        && Objects.equals(messageName, that.messageName)
        && Objects.equals(correlationKey, that.correlationKey);
  }
}
