/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.messagesubscription;

import io.camunda.webapps.schema.entities.BeforeVersion880;
import io.camunda.webapps.schema.entities.incident.ErrorType;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

public class MessageSubscriptionMetadataEntity {

  /** Message data. */
  @BeforeVersion880 private String messageName;

  @BeforeVersion880 private String correlationKey;

  /**
   * @deprecated since 8.9
   */
  @BeforeVersion880 @Deprecated private String jobType;

  /**
   * @deprecated since 8.9
   */
  @BeforeVersion880 @Deprecated private Integer jobRetries;

  /**
   * @deprecated since 8.9
   */
  @BeforeVersion880 @Deprecated private String jobWorker;

  /**
   * @deprecated since 8.9
   */
  @BeforeVersion880 @Deprecated private OffsetDateTime jobDeadline;

  /**
   * @deprecated since 8.9
   */
  @BeforeVersion880 @Deprecated private Map<String, String> jobCustomHeaders;

  /**
   * @deprecated since 8.9
   */
  @BeforeVersion880 @Deprecated private Long jobKey;

  /**
   * @deprecated since 8.9
   */
  @BeforeVersion880 @Deprecated private ErrorType incidentErrorType;

  /**
   * @deprecated since 8.9
   */
  @BeforeVersion880 @Deprecated private String incidentErrorMessage;

  public String getMessageName() {
    return messageName;
  }

  public MessageSubscriptionMetadataEntity setMessageName(final String messageName) {
    this.messageName = messageName;
    return this;
  }

  public String getCorrelationKey() {
    return correlationKey;
  }

  public MessageSubscriptionMetadataEntity setCorrelationKey(final String correlationKey) {
    this.correlationKey = correlationKey;
    return this;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public String getJobType() {
    return jobType;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public MessageSubscriptionMetadataEntity setJobType(final String jobType) {
    this.jobType = jobType;
    return this;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public Integer getJobRetries() {
    return jobRetries;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public MessageSubscriptionMetadataEntity setJobRetries(final Integer jobRetries) {
    this.jobRetries = jobRetries;
    return this;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public String getJobWorker() {
    return jobWorker;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public MessageSubscriptionMetadataEntity setJobWorker(final String jobWorker) {
    this.jobWorker = jobWorker;
    return this;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public OffsetDateTime getJobDeadline() {
    return jobDeadline;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public MessageSubscriptionMetadataEntity setJobDeadline(final OffsetDateTime jobDeadline) {
    this.jobDeadline = jobDeadline;
    return this;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public Map<String, String> getJobCustomHeaders() {
    return jobCustomHeaders;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public MessageSubscriptionMetadataEntity setJobCustomHeaders(
      final Map<String, String> jobCustomHeaders) {
    this.jobCustomHeaders = jobCustomHeaders;
    return this;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public Long getJobKey() {
    return jobKey;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public void setJobKey(final Long jobKey) {
    this.jobKey = jobKey;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public ErrorType getIncidentErrorType() {
    return incidentErrorType;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public void setIncidentErrorType(final ErrorType incidentErrorType) {
    this.incidentErrorType = incidentErrorType;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public String getIncidentErrorMessage() {
    return incidentErrorMessage;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public void setIncidentErrorMessage(final String incidentErrorMessage) {
    this.incidentErrorMessage = incidentErrorMessage;
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
    final MessageSubscriptionMetadataEntity that = (MessageSubscriptionMetadataEntity) o;
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
