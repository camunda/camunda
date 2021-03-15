/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto;

import java.time.OffsetDateTime;
import java.util.Map;

import org.camunda.operate.entities.ErrorType;
import org.camunda.operate.entities.EventMetadataEntity;
import org.camunda.operate.util.ConversionUtils;

@Deprecated
public class EventMetadataDto {

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
  private String jobId;

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

  public String getJobId() {
    return jobId;
  }

  public void setJobId(String jobId) {
    this.jobId = jobId;
  }

  public static EventMetadataDto createFrom(EventMetadataEntity eventMetadataEntity) {
    EventMetadataDto eventMetadata = new EventMetadataDto();
    eventMetadata.setIncidentErrorMessage(eventMetadataEntity.getIncidentErrorMessage());
    eventMetadata.setIncidentErrorType(eventMetadataEntity.getIncidentErrorType());
    eventMetadata.setJobCustomHeaders(eventMetadataEntity.getJobCustomHeaders());
    eventMetadata.setJobDeadline(eventMetadataEntity.getJobDeadline());
    eventMetadata.setJobId(ConversionUtils.toStringOrNull(eventMetadataEntity.getJobKey()));
    eventMetadata.setJobRetries(eventMetadataEntity.getJobRetries());
    eventMetadata.setJobType(eventMetadataEntity.getJobType());
    eventMetadata.setJobWorker(eventMetadataEntity.getJobWorker());
    return eventMetadata;
  }

}
