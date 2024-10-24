/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.process;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_USER_TASK;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.persistence.AssigneeOperationDto;
import io.camunda.optimize.dto.optimize.persistence.CandidateGroupOperationDto;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class FlowNodeInstanceDto implements Serializable, OptimizeDto {

  private String flowNodeInstanceId;
  private String flowNodeId;
  private String flowNodeType;
  private String processInstanceId;
  private Long totalDurationInMs;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private Boolean canceled;

  // Duplicated fields from the process instance that are used for view filtering
  private String definitionKey;
  private String definitionVersion;
  private String tenantId;

  // Note that engine is transient and only used for correct "routing" during import
  @JsonIgnore private String engine;

  private String userTaskInstanceId;

  private OffsetDateTime dueDate;

  private String deleteReason;

  private String assignee;
  private List<String> candidateGroups = new ArrayList<>();
  private List<AssigneeOperationDto> assigneeOperations = new ArrayList<>();
  private List<CandidateGroupOperationDto> candidateGroupOperations = new ArrayList<>();

  private Long idleDurationInMs;
  private Long workDurationInMs;

  // engine activity instance specific ctor with mandatory and default but nullable fields
  public FlowNodeInstanceDto(
      final String definitionKey,
      final String definitionVersion,
      final String tenantId,
      final String engine,
      final String processInstanceId,
      final String flowNodeId,
      final String flowNodeType,
      final String flowNodeInstanceId,
      final String userTaskInstanceId) {
    if (definitionKey == null) {
      throw new IllegalArgumentException("Definition key cannot be null");
    }
    if (definitionVersion == null) {
      throw new IllegalArgumentException("Definition version cannot be null");
    }
    if (engine == null) {
      throw new IllegalArgumentException("Engine cannot be null");
    }
    if (processInstanceId == null) {
      throw new IllegalArgumentException("ProcessInstanceId cannot be null");
    }
    if (flowNodeId == null) {
      throw new IllegalArgumentException("FlowNodeId cannot be null");
    }
    if (flowNodeType == null) {
      throw new IllegalArgumentException("FlowNodeType cannot be null");
    }
    if (flowNodeInstanceId == null) {
      throw new IllegalArgumentException("FlowNodeInstanceId cannot be null");
    }

    this.flowNodeInstanceId = flowNodeInstanceId;
    this.flowNodeId = flowNodeId;
    this.flowNodeType = flowNodeType;
    this.processInstanceId = processInstanceId;
    this.definitionKey = definitionKey;
    this.definitionVersion = definitionVersion;
    this.tenantId = tenantId;
    this.engine = engine;
    this.userTaskInstanceId = userTaskInstanceId;
  }

  // zeebe process activity instance specific ctor with mandatory fields
  public FlowNodeInstanceDto(
      final String definitionKey,
      final String definitionVersion,
      final String tenantId,
      final String processInstanceId,
      final String flowNodeId,
      final String flowNodeType,
      final String flowNodeInstanceId) {
    if (definitionKey == null) {
      throw new IllegalArgumentException("Definition key cannot be null");
    }
    if (definitionVersion == null) {
      throw new IllegalArgumentException("Definition version cannot be null");
    }
    if (processInstanceId == null) {
      throw new IllegalArgumentException("ProcessInstanceId cannot be null");
    }
    if (flowNodeId == null) {
      throw new IllegalArgumentException("FlowNodeId cannot be null");
    }
    if (flowNodeType == null) {
      throw new IllegalArgumentException("FlowNodeType cannot be null");
    }
    if (flowNodeInstanceId == null) {
      throw new IllegalArgumentException("FlowNodeInstanceId cannot be null");
    }

    this.flowNodeInstanceId = flowNodeInstanceId;
    this.flowNodeId = flowNodeId;
    this.flowNodeType = flowNodeType;
    this.processInstanceId = processInstanceId;
    this.definitionKey = definitionKey;
    this.definitionVersion = definitionVersion;
    this.tenantId = tenantId;
  }

  // engine user task specific ctor with mandatory fields
  public FlowNodeInstanceDto(
      final String definitionKey,
      final String engine,
      final String processInstanceId,
      final String flowNodeId,
      final String flowNodeInstanceId,
      final String userTaskInstanceId) {
    if (definitionKey == null) {
      throw new IllegalArgumentException("Definition key cannot be null");
    }
    if (engine == null) {
      throw new IllegalArgumentException("Engine cannot be null");
    }
    if (processInstanceId == null) {
      throw new IllegalArgumentException("ProcessInstanceId cannot be null");
    }
    if (flowNodeId == null) {
      throw new IllegalArgumentException("FlowNodeId cannot be null");
    }
    if (flowNodeInstanceId == null) {
      throw new IllegalArgumentException("FlowNodeInstanceId cannot be null");
    }
    if (userTaskInstanceId == null) {
      throw new IllegalArgumentException("UserTaskInstanceId cannot be null");
    }

    this.processInstanceId = processInstanceId;
    this.definitionKey = definitionKey;
    this.engine = engine;
    this.flowNodeId = flowNodeId;
    this.flowNodeInstanceId = flowNodeInstanceId;
    flowNodeType = FLOW_NODE_TYPE_USER_TASK;
    this.userTaskInstanceId = userTaskInstanceId;
  }

  // engine identity link log specific ctor with mandatory fields
  public FlowNodeInstanceDto(
      final String definitionKey,
      final String engine,
      final String processInstanceId,
      final String userTaskInstanceId) {
    if (definitionKey == null) {
      throw new IllegalArgumentException("Definition key cannot be null");
    }
    if (engine == null) {
      throw new IllegalArgumentException("Engine cannot be null");
    }
    if (processInstanceId == null) {
      throw new IllegalArgumentException("ProcessInstanceId cannot be null");
    }
    if (userTaskInstanceId == null) {
      throw new IllegalArgumentException("UserTaskInstanceId cannot be null");
    }

    this.processInstanceId = processInstanceId;
    this.definitionKey = definitionKey;
    this.engine = engine;
    flowNodeType = FLOW_NODE_TYPE_USER_TASK;
    this.userTaskInstanceId = userTaskInstanceId;
  }

  public FlowNodeInstanceDto(
      final String flowNodeInstanceId,
      final String flowNodeId,
      final String flowNodeType,
      final String processInstanceId,
      final Long totalDurationInMs,
      final OffsetDateTime startDate,
      final OffsetDateTime endDate,
      final Boolean canceled,
      final String definitionKey,
      final String definitionVersion,
      final String tenantId,
      final String engine,
      final String userTaskInstanceId,
      final OffsetDateTime dueDate,
      final String deleteReason,
      final String assignee,
      final List<String> candidateGroups,
      final List<AssigneeOperationDto> assigneeOperations,
      final List<CandidateGroupOperationDto> candidateGroupOperations,
      final Long idleDurationInMs,
      final Long workDurationInMs) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    this.flowNodeId = flowNodeId;
    this.flowNodeType = flowNodeType;
    this.processInstanceId = processInstanceId;
    this.totalDurationInMs = totalDurationInMs;
    this.startDate = startDate;
    this.endDate = endDate;
    this.canceled = canceled;
    this.definitionKey = definitionKey;
    this.definitionVersion = definitionVersion;
    this.tenantId = tenantId;
    this.engine = engine;
    this.userTaskInstanceId = userTaskInstanceId;
    this.dueDate = dueDate;
    this.deleteReason = deleteReason;
    this.assignee = assignee;
    this.candidateGroups = candidateGroups;
    this.assigneeOperations = assigneeOperations;
    this.candidateGroupOperations = candidateGroupOperations;
    this.idleDurationInMs = idleDurationInMs;
    this.workDurationInMs = workDurationInMs;
  }

  public FlowNodeInstanceDto() {}

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

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
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

  public String getEngine() {
    return engine;
  }

  @JsonIgnore
  public void setEngine(final String engine) {
    this.engine = engine;
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

  public List<AssigneeOperationDto> getAssigneeOperations() {
    return assigneeOperations;
  }

  public void setAssigneeOperations(final List<AssigneeOperationDto> assigneeOperations) {
    this.assigneeOperations = assigneeOperations;
  }

  public List<CandidateGroupOperationDto> getCandidateGroupOperations() {
    return candidateGroupOperations;
  }

  public void setCandidateGroupOperations(
      final List<CandidateGroupOperationDto> candidateGroupOperations) {
    this.candidateGroupOperations = candidateGroupOperations;
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

  protected boolean canEqual(final Object other) {
    return other instanceof FlowNodeInstanceDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "FlowNodeInstanceDto(flowNodeInstanceId="
        + getFlowNodeInstanceId()
        + ", flowNodeId="
        + getFlowNodeId()
        + ", flowNodeType="
        + getFlowNodeType()
        + ", processInstanceId="
        + getProcessInstanceId()
        + ", totalDurationInMs="
        + getTotalDurationInMs()
        + ", startDate="
        + getStartDate()
        + ", endDate="
        + getEndDate()
        + ", canceled="
        + getCanceled()
        + ", definitionKey="
        + getDefinitionKey()
        + ", definitionVersion="
        + getDefinitionVersion()
        + ", tenantId="
        + getTenantId()
        + ", engine="
        + getEngine()
        + ", userTaskInstanceId="
        + getUserTaskInstanceId()
        + ", dueDate="
        + getDueDate()
        + ", deleteReason="
        + getDeleteReason()
        + ", assignee="
        + getAssignee()
        + ", candidateGroups="
        + getCandidateGroups()
        + ", assigneeOperations="
        + getAssigneeOperations()
        + ", candidateGroupOperations="
        + getCandidateGroupOperations()
        + ", idleDurationInMs="
        + getIdleDurationInMs()
        + ", workDurationInMs="
        + getWorkDurationInMs()
        + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String flowNodeInstanceId = "flowNodeInstanceId";
    public static final String flowNodeId = "flowNodeId";
    public static final String flowNodeType = "flowNodeType";
    public static final String processInstanceId = "processInstanceId";
    public static final String totalDurationInMs = "totalDurationInMs";
    public static final String startDate = "startDate";
    public static final String endDate = "endDate";
    public static final String canceled = "canceled";
    public static final String definitionKey = "definitionKey";
    public static final String definitionVersion = "definitionVersion";
    public static final String tenantId = "tenantId";
    public static final String engine = "engine";
    public static final String userTaskInstanceId = "userTaskInstanceId";
    public static final String dueDate = "dueDate";
    public static final String deleteReason = "deleteReason";
    public static final String assignee = "assignee";
    public static final String candidateGroups = "candidateGroups";
    public static final String assigneeOperations = "assigneeOperations";
    public static final String candidateGroupOperations = "candidateGroupOperations";
    public static final String idleDurationInMs = "idleDurationInMs";
    public static final String workDurationInMs = "workDurationInMs";
  }
}
