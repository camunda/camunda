/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.camunda.webapps.schema.entities.event.EventEntity;
import io.camunda.webapps.schema.entities.event.EventMetadataEntity;
import io.camunda.webapps.schema.entities.operate.FlowNodeType;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class JobFlowNodeInstanceMetadataDto extends FlowNodeInstanceMetadataDto
    implements FlowNodeInstanceMetadata {

  private OffsetDateTime jobDeadline;

  /** Job data. */
  private String jobType;

  private Integer jobRetries;
  private String jobWorker;
  private Map<String, String> jobCustomHeaders;

  public JobFlowNodeInstanceMetadataDto(
      final String flowNodeId,
      final String flowNodeInstanceId,
      final FlowNodeType flowNodeType,
      final OffsetDateTime startDate,
      final OffsetDateTime endDate,
      final EventEntity event) {
    super(flowNodeId, flowNodeInstanceId, flowNodeType, startDate, endDate, event);
    final EventMetadataEntity eventMetadataEntity = event.getMetadata();
    if (eventMetadataEntity != null) {
      setJobCustomHeaders(eventMetadataEntity.getJobCustomHeaders())
          .setJobDeadline(eventMetadataEntity.getJobDeadline())
          .setJobRetries(eventMetadataEntity.getJobRetries())
          .setJobType(eventMetadataEntity.getJobType())
          .setJobWorker(eventMetadataEntity.getJobWorker());
    }
  }

  public JobFlowNodeInstanceMetadataDto() {
    super();
  }

  public String getJobType() {
    return jobType;
  }

  public JobFlowNodeInstanceMetadataDto setJobType(final String jobType) {
    this.jobType = jobType;
    return this;
  }

  public Integer getJobRetries() {
    return jobRetries;
  }

  public JobFlowNodeInstanceMetadataDto setJobRetries(final Integer jobRetries) {
    this.jobRetries = jobRetries;
    return this;
  }

  public String getJobWorker() {
    return jobWorker;
  }

  public JobFlowNodeInstanceMetadataDto setJobWorker(final String jobWorker) {
    this.jobWorker = jobWorker;
    return this;
  }

  public OffsetDateTime getJobDeadline() {
    return jobDeadline;
  }

  public JobFlowNodeInstanceMetadataDto setJobDeadline(final OffsetDateTime jobDeadline) {
    this.jobDeadline = jobDeadline;
    return this;
  }

  public Map<String, String> getJobCustomHeaders() {
    return jobCustomHeaders;
  }

  public JobFlowNodeInstanceMetadataDto setJobCustomHeaders(
      final Map<String, String> jobCustomHeaders) {
    this.jobCustomHeaders = jobCustomHeaders;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(), jobDeadline, jobType, jobRetries, jobWorker, jobCustomHeaders);
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
    final JobFlowNodeInstanceMetadataDto that = (JobFlowNodeInstanceMetadataDto) o;
    return Objects.equals(jobDeadline, that.jobDeadline)
        && Objects.equals(jobType, that.jobType)
        && Objects.equals(jobRetries, that.jobRetries)
        && Objects.equals(jobWorker, that.jobWorker)
        && Objects.equals(jobCustomHeaders, that.jobCustomHeaders);
  }

  @Override
  public String toString() {
    return "JobFlowNodeInstanceMetadataDto{"
        + "jobDeadline="
        + jobDeadline
        + ", jobType='"
        + jobType
        + '\''
        + ", jobRetries="
        + jobRetries
        + ", jobWorker='"
        + jobWorker
        + '\''
        + ", jobCustomHeaders="
        + jobCustomHeaders
        + "} "
        + super.toString();
  }
}
