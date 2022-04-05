/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

import java.time.OffsetDateTime;
import java.util.Map;

public class EventMetadataEntity {

  /**
  * Job data.
  */
  private String jobType;
  private Integer jobRetries;
  private String jobWorker;
  private OffsetDateTime jobDeadline;
  private Map<String, String> jobCustomHeaders;
  
  /**
  * Incident data.
  */
  private ErrorType incidentErrorType;
  private String incidentErrorMessage;
  private Long jobKey;

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

  public Long getJobKey() {
    return jobKey;
  }

  public void setJobKey(Long jobKey) {
    this.jobKey = jobKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    EventMetadataEntity that = (EventMetadataEntity) o;

    if (jobType != null ? !jobType.equals(that.jobType) : that.jobType != null)
      return false;
    if (jobRetries != null ? !jobRetries.equals(that.jobRetries) : that.jobRetries != null)
      return false;
    if (jobWorker != null ? !jobWorker.equals(that.jobWorker) : that.jobWorker != null)
      return false;
    if (jobDeadline != null ? !jobDeadline.equals(that.jobDeadline) : that.jobDeadline != null)
      return false;
    if (jobCustomHeaders != null ? !jobCustomHeaders.equals(that.jobCustomHeaders) : that.jobCustomHeaders != null)
      return false;
    if (incidentErrorType != null ? !incidentErrorType.equals(that.incidentErrorType) : that.incidentErrorType != null)
      return false;
    if (incidentErrorMessage != null ? !incidentErrorMessage.equals(that.incidentErrorMessage) : that.incidentErrorMessage != null)
      return false;
    return jobKey != null ? jobKey.equals(that.jobKey) : that.jobKey == null;
  }

  @Override
  public int hashCode() {
    int result = jobType != null ? jobType.hashCode() : 0;
    result = 31 * result + (jobRetries != null ? jobRetries.hashCode() : 0);
    result = 31 * result + (jobWorker != null ? jobWorker.hashCode() : 0);
    result = 31 * result + (jobDeadline != null ? jobDeadline.hashCode() : 0);
    result = 31 * result + (jobCustomHeaders != null ? jobCustomHeaders.hashCode() : 0);
    result = 31 * result + (incidentErrorType != null ? incidentErrorType.hashCode() : 0);
    result = 31 * result + (incidentErrorMessage != null ? incidentErrorMessage.hashCode() : 0);
    result = 31 * result + (jobKey != null ? jobKey.hashCode() : 0);
    return result;
  }
}
