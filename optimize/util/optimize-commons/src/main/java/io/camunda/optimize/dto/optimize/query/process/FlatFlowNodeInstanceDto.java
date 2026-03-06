/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.process;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A flattened flow node instance document that combines process instance context with flow node
 * instance-specific fields, for storing in the FlatFlowNodeInstanceIndex.
 */
public class FlatFlowNodeInstanceDto implements OptimizeDto {

  private String processDefinitionKey;
  private String processDefinitionVersion;
  private String processDefinitionId;
  private String processInstanceId;
  private String flowNodeInstanceId;
  private String flowNodeId;
  private String flowNodeType;
  private Long totalDurationInMs;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private Boolean canceled;
  private String definitionKey;
  private String definitionVersion;
  private String tenantId;
  private String userTaskInstanceId;
  private OffsetDateTime dueDate;
  private String deleteReason;
  private String assignee;
  private List<String> candidateGroups;
  private Long idleDurationInMs;
  private Long workDurationInMs;
  private int partition;
  private int ordinal;
  @JsonIgnore private boolean isNew;

  public FlatFlowNodeInstanceDto() {}

  public static FlatFlowNodeInstanceDto fromProcessInstanceAndFlowNode(
      final String processDefinitionKey,
      final String processDefinitionVersion,
      final String processDefinitionId,
      final String processInstanceId,
      final FlowNodeInstanceDto flowNode) {
    final FlatFlowNodeInstanceDto dto = new FlatFlowNodeInstanceDto();
    dto.setProcessDefinitionKey(processDefinitionKey);
    dto.setProcessDefinitionVersion(processDefinitionVersion);
    dto.setProcessDefinitionId(processDefinitionId);
    dto.setProcessInstanceId(processInstanceId);
    dto.setFlowNodeInstanceId(flowNode.getFlowNodeInstanceId());
    dto.setFlowNodeId(flowNode.getFlowNodeId());
    dto.setFlowNodeType(flowNode.getFlowNodeType());
    dto.setTotalDurationInMs(flowNode.getTotalDurationInMs());
    dto.setStartDate(flowNode.getStartDate());
    dto.setEndDate(flowNode.getEndDate());
    dto.setCanceled(flowNode.getCanceled());
    dto.setDefinitionKey(flowNode.getDefinitionKey());
    dto.setDefinitionVersion(flowNode.getDefinitionVersion());
    dto.setTenantId(flowNode.getTenantId());
    dto.setUserTaskInstanceId(flowNode.getUserTaskInstanceId());
    dto.setDueDate(flowNode.getDueDate());
    dto.setDeleteReason(flowNode.getDeleteReason());
    dto.setAssignee(flowNode.getAssignee());
    dto.setCandidateGroups(flowNode.getCandidateGroups());
    dto.setIdleDurationInMs(flowNode.getIdleDurationInMs());
    dto.setWorkDurationInMs(flowNode.getWorkDurationInMs());
    return dto;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setProcessDefinitionVersion(final String processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  public void setFlowNodeInstanceId(final String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public void setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }

  public String getFlowNodeType() {
    return flowNodeType;
  }

  public void setFlowNodeType(final String flowNodeType) {
    this.flowNodeType = flowNodeType;
  }

  public Long getTotalDurationInMs() {
    return totalDurationInMs;
  }

  public void setTotalDurationInMs(final Long totalDurationInMs) {
    this.totalDurationInMs = totalDurationInMs;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public void setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public void setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
  }

  public Boolean getCanceled() {
    return canceled;
  }

  public void setCanceled(final Boolean canceled) {
    this.canceled = canceled;
  }

  public String getDefinitionKey() {
    return definitionKey;
  }

  public void setDefinitionKey(final String definitionKey) {
    this.definitionKey = definitionKey;
  }

  public String getDefinitionVersion() {
    return definitionVersion;
  }

  public void setDefinitionVersion(final String definitionVersion) {
    this.definitionVersion = definitionVersion;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public String getUserTaskInstanceId() {
    return userTaskInstanceId;
  }

  public void setUserTaskInstanceId(final String userTaskInstanceId) {
    this.userTaskInstanceId = userTaskInstanceId;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public void setDueDate(final OffsetDateTime dueDate) {
    this.dueDate = dueDate;
  }

  public String getDeleteReason() {
    return deleteReason;
  }

  public void setDeleteReason(final String deleteReason) {
    this.deleteReason = deleteReason;
  }

  public String getAssignee() {
    return assignee;
  }

  public void setAssignee(final String assignee) {
    this.assignee = assignee;
  }

  public List<String> getCandidateGroups() {
    return candidateGroups;
  }

  public void setCandidateGroups(final List<String> candidateGroups) {
    this.candidateGroups = candidateGroups;
  }

  public Long getIdleDurationInMs() {
    return idleDurationInMs;
  }

  public void setIdleDurationInMs(final Long idleDurationInMs) {
    this.idleDurationInMs = idleDurationInMs;
  }

  public Long getWorkDurationInMs() {
    return workDurationInMs;
  }

  public void setWorkDurationInMs(final Long workDurationInMs) {
    this.workDurationInMs = workDurationInMs;
  }

  public int getPartition() {
    return partition;
  }

  public void setPartition(final int partition) {
    this.partition = partition;
  }

  public int getOrdinal() {
    return ordinal;
  }

  public void setOrdinal(final int ordinal) {
    this.ordinal = ordinal;
  }

  @JsonIgnore
  public boolean isNew() {
    return isNew;
  }

  @JsonIgnore
  public void setNew(final boolean isNew) {
    this.isNew = isNew;
  }
}
