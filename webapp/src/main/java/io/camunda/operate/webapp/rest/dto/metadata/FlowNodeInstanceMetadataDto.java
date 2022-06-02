/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.metadata;

import io.camunda.operate.entities.EventEntity;
import io.camunda.operate.entities.EventMetadataEntity;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeType;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

public class FlowNodeInstanceMetadataDto {

  /**
   * Flow node data.
   */
  private String flowNodeId;
  private String flowNodeInstanceId;
  private FlowNodeType flowNodeType;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private String calledProcessInstanceId;
  private String calledProcessDefinitionName;
  private String calledDecisionInstanceId;
  private String calledDecisionDefinitionName;

  private String eventId;
  /**
   * Job data.
   */
  private String jobType;
  private Integer jobRetries;
  private String jobWorker;
  private OffsetDateTime jobDeadline;
  private Map<String, String> jobCustomHeaders;

  /**
   * Message data.
   */
  private String messageName;
  private String correlationKey;

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public FlowNodeInstanceMetadataDto setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  public FlowNodeInstanceMetadataDto setFlowNodeInstanceId(final String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    return this;
  }

  public FlowNodeType getFlowNodeType() {
    return flowNodeType;
  }

  public FlowNodeInstanceMetadataDto setFlowNodeType(
      final FlowNodeType flowNodeType) {
    this.flowNodeType = flowNodeType;
    return this;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public FlowNodeInstanceMetadataDto setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public FlowNodeInstanceMetadataDto setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  public String getCalledProcessInstanceId() {
    return calledProcessInstanceId;
  }

  public FlowNodeInstanceMetadataDto setCalledProcessInstanceId(
      final String calledProcessInstanceId) {
    this.calledProcessInstanceId = calledProcessInstanceId;
    return this;
  }

  public String getCalledProcessDefinitionName() {
    return calledProcessDefinitionName;
  }

  public FlowNodeInstanceMetadataDto setCalledProcessDefinitionName(
      final String calledProcessDefinitionName) {
    this.calledProcessDefinitionName = calledProcessDefinitionName;
    return this;
  }

  public String getCalledDecisionInstanceId() {
    return calledDecisionInstanceId;
  }

  public FlowNodeInstanceMetadataDto setCalledDecisionInstanceId(
      final String calledDecisionInstanceId) {
    this.calledDecisionInstanceId = calledDecisionInstanceId;
    return this;
  }

  public String getCalledDecisionDefinitionName() {
    return calledDecisionDefinitionName;
  }

  public FlowNodeInstanceMetadataDto setCalledDecisionDefinitionName(
      final String calledDecisionDefinitionName) {
    this.calledDecisionDefinitionName = calledDecisionDefinitionName;
    return this;
  }

  public String getEventId() {
    return eventId;
  }

  public FlowNodeInstanceMetadataDto setEventId(final String eventId) {
    this.eventId = eventId;
    return this;
  }

  public String getJobType() {
    return jobType;
  }

  public FlowNodeInstanceMetadataDto setJobType(final String jobType) {
    this.jobType = jobType;
    return this;
  }

  public Integer getJobRetries() {
    return jobRetries;
  }

  public FlowNodeInstanceMetadataDto setJobRetries(final Integer jobRetries) {
    this.jobRetries = jobRetries;
    return this;
  }

  public String getJobWorker() {
    return jobWorker;
  }

  public FlowNodeInstanceMetadataDto setJobWorker(final String jobWorker) {
    this.jobWorker = jobWorker;
    return this;
  }

  public OffsetDateTime getJobDeadline() {
    return jobDeadline;
  }

  public FlowNodeInstanceMetadataDto setJobDeadline(final OffsetDateTime jobDeadline) {
    this.jobDeadline = jobDeadline;
    return this;
  }

  public Map<String, String> getJobCustomHeaders() {
    return jobCustomHeaders;
  }

  public FlowNodeInstanceMetadataDto setJobCustomHeaders(
      final Map<String, String> jobCustomHeaders) {
    this.jobCustomHeaders = jobCustomHeaders;
    return this;
  }

  public String getMessageName() {
    return messageName;
  }

  public FlowNodeInstanceMetadataDto setMessageName(final String messageName) {
    this.messageName = messageName;
    return this;
  }

  public String getCorrelationKey() {
    return correlationKey;
  }

  public FlowNodeInstanceMetadataDto setCorrelationKey(final String correlationKey) {
    this.correlationKey = correlationKey;
    return this;
  }

  public static FlowNodeInstanceMetadataDto createFrom(FlowNodeInstanceEntity flowNodeInstance,
      EventEntity eventEntity, String calledProcessInstanceId, String calledProcessDefinitionName,
      String calledDecisionInstanceId, String calledDecisionDefinitionName) {
    FlowNodeInstanceMetadataDto metadataDto = new FlowNodeInstanceMetadataDto()
    //flow node instance data
        .setFlowNodeInstanceId(flowNodeInstance.getId())
        .setFlowNodeId(flowNodeInstance.getFlowNodeId())
        .setFlowNodeType(flowNodeInstance.getType())
        .setStartDate(flowNodeInstance.getStartDate())
        .setEndDate(flowNodeInstance.getEndDate());
    if (calledProcessInstanceId != null) {
      metadataDto.setCalledProcessInstanceId(calledProcessInstanceId);
    }
    if (calledProcessDefinitionName != null) {
      metadataDto.setCalledProcessDefinitionName(calledProcessDefinitionName);
    }
    if (calledDecisionInstanceId != null) {
      metadataDto.setCalledDecisionInstanceId(calledDecisionInstanceId);
    }
    if (calledDecisionDefinitionName != null) {
      metadataDto.setCalledDecisionDefinitionName(calledDecisionDefinitionName);
    }

    //last event data
    metadataDto.setEventId(eventEntity.getId());
    EventMetadataEntity eventMetadataEntity = eventEntity.getMetadata();
    if (eventMetadataEntity != null) {
      metadataDto.setJobCustomHeaders(eventMetadataEntity.getJobCustomHeaders())
          .setJobDeadline(eventMetadataEntity.getJobDeadline())
          .setJobRetries(eventMetadataEntity.getJobRetries())
          .setJobType(eventMetadataEntity.getJobType())
          .setJobWorker(eventMetadataEntity.getJobWorker())
          .setMessageName(eventMetadataEntity.getMessageName())
          .setCorrelationKey(eventMetadataEntity.getCorrelationKey());
    }

    return metadataDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FlowNodeInstanceMetadataDto that = (FlowNodeInstanceMetadataDto) o;
    return Objects.equals(flowNodeId, that.flowNodeId) &&
        Objects.equals(flowNodeInstanceId, that.flowNodeInstanceId) &&
        flowNodeType == that.flowNodeType &&
        Objects.equals(startDate, that.startDate) &&
        Objects.equals(endDate, that.endDate) &&
        Objects.equals(calledProcessInstanceId, that.calledProcessInstanceId) &&
        Objects.equals(calledProcessDefinitionName, that.calledProcessDefinitionName) &&
        Objects.equals(calledDecisionInstanceId, that.calledDecisionInstanceId) &&
        Objects.equals(calledDecisionDefinitionName, that.calledDecisionDefinitionName) &&
        Objects.equals(eventId, that.eventId) &&
        Objects.equals(jobType, that.jobType) &&
        Objects.equals(jobRetries, that.jobRetries) &&
        Objects.equals(jobWorker, that.jobWorker) &&
        Objects.equals(jobDeadline, that.jobDeadline) &&
        Objects.equals(jobCustomHeaders, that.jobCustomHeaders) &&
        Objects.equals(messageName, that.messageName) &&
        Objects.equals(correlationKey, that.correlationKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(flowNodeId, flowNodeInstanceId, flowNodeType, startDate, endDate,
        calledProcessInstanceId, calledProcessDefinitionName, calledDecisionInstanceId,
        calledDecisionDefinitionName, eventId, jobType, jobRetries, jobWorker, jobDeadline,
        jobCustomHeaders, messageName, correlationKey);
  }

  @Override
  public String toString() {
    return "FlowNodeInstanceMetadataDto{" +
        "flowNodeId='" + flowNodeId + '\'' +
        ", flowNodeInstanceId='" + flowNodeInstanceId + '\'' +
        ", flowNodeType=" + flowNodeType +
        ", startDate=" + startDate +
        ", endDate=" + endDate +
        ", calledProcessInstanceId='" + calledProcessInstanceId + '\'' +
        ", calledProcessDefinitionName='" + calledProcessDefinitionName + '\'' +
        ", calledDecisionInstanceId='" + calledDecisionInstanceId + '\'' +
        ", calledDecisionDefinitionName='" + calledDecisionDefinitionName + '\'' +
        ", eventId='" + eventId + '\'' +
        ", jobType='" + jobType + '\'' +
        ", jobRetries=" + jobRetries +
        ", jobWorker='" + jobWorker + '\'' +
        ", jobDeadline=" + jobDeadline +
        ", jobCustomHeaders=" + jobCustomHeaders +
        ", messageName='" + messageName + '\'' +
        ", correlationKey='" + correlationKey + '\'' +
        '}';
  }
}
