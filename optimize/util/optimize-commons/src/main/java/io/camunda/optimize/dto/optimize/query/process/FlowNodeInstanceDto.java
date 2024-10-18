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
      String flowNodeInstanceId,
      String flowNodeId,
      String flowNodeType,
      String processInstanceId,
      Long totalDurationInMs,
      OffsetDateTime startDate,
      OffsetDateTime endDate,
      Boolean canceled,
      String definitionKey,
      String definitionVersion,
      String tenantId,
      String engine,
      String userTaskInstanceId,
      OffsetDateTime dueDate,
      String deleteReason,
      String assignee,
      List<String> candidateGroups,
      List<AssigneeOperationDto> assigneeOperations,
      List<CandidateGroupOperationDto> candidateGroupOperations,
      Long idleDurationInMs,
      Long workDurationInMs) {
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
    return this.flowNodeInstanceId;
  }

  public String getFlowNodeId() {
    return this.flowNodeId;
  }

  public String getFlowNodeType() {
    return this.flowNodeType;
  }

  public String getProcessInstanceId() {
    return this.processInstanceId;
  }

  public Long getTotalDurationInMs() {
    return this.totalDurationInMs;
  }

  public OffsetDateTime getStartDate() {
    return this.startDate;
  }

  public OffsetDateTime getEndDate() {
    return this.endDate;
  }

  public Boolean getCanceled() {
    return this.canceled;
  }

  public String getDefinitionKey() {
    return this.definitionKey;
  }

  public String getDefinitionVersion() {
    return this.definitionVersion;
  }

  public String getTenantId() {
    return this.tenantId;
  }

  public String getEngine() {
    return this.engine;
  }

  public String getUserTaskInstanceId() {
    return this.userTaskInstanceId;
  }

  public OffsetDateTime getDueDate() {
    return this.dueDate;
  }

  public String getDeleteReason() {
    return this.deleteReason;
  }

  public String getAssignee() {
    return this.assignee;
  }

  public List<String> getCandidateGroups() {
    return this.candidateGroups;
  }

  public List<AssigneeOperationDto> getAssigneeOperations() {
    return this.assigneeOperations;
  }

  public List<CandidateGroupOperationDto> getCandidateGroupOperations() {
    return this.candidateGroupOperations;
  }

  public Long getIdleDurationInMs() {
    return this.idleDurationInMs;
  }

  public Long getWorkDurationInMs() {
    return this.workDurationInMs;
  }

  public void setFlowNodeInstanceId(String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
  }

  public void setFlowNodeId(String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }

  public void setFlowNodeType(String flowNodeType) {
    this.flowNodeType = flowNodeType;
  }

  public void setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public void setTotalDurationInMs(Long totalDurationInMs) {
    this.totalDurationInMs = totalDurationInMs;
  }

  public void setStartDate(OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  public void setEndDate(OffsetDateTime endDate) {
    this.endDate = endDate;
  }

  public void setCanceled(Boolean canceled) {
    this.canceled = canceled;
  }

  public void setDefinitionKey(String definitionKey) {
    this.definitionKey = definitionKey;
  }

  public void setDefinitionVersion(String definitionVersion) {
    this.definitionVersion = definitionVersion;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  @JsonIgnore
  public void setEngine(String engine) {
    this.engine = engine;
  }

  public void setUserTaskInstanceId(String userTaskInstanceId) {
    this.userTaskInstanceId = userTaskInstanceId;
  }

  public void setDueDate(OffsetDateTime dueDate) {
    this.dueDate = dueDate;
  }

  public void setDeleteReason(String deleteReason) {
    this.deleteReason = deleteReason;
  }

  public void setAssignee(String assignee) {
    this.assignee = assignee;
  }

  public void setCandidateGroups(List<String> candidateGroups) {
    this.candidateGroups = candidateGroups;
  }

  public void setAssigneeOperations(List<AssigneeOperationDto> assigneeOperations) {
    this.assigneeOperations = assigneeOperations;
  }

  public void setCandidateGroupOperations(
      List<CandidateGroupOperationDto> candidateGroupOperations) {
    this.candidateGroupOperations = candidateGroupOperations;
  }

  public void setIdleDurationInMs(Long idleDurationInMs) {
    this.idleDurationInMs = idleDurationInMs;
  }

  public void setWorkDurationInMs(Long workDurationInMs) {
    this.workDurationInMs = workDurationInMs;
  }

  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof FlowNodeInstanceDto;
  }

  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  public String toString() {
    return "FlowNodeInstanceDto(flowNodeInstanceId="
        + this.getFlowNodeInstanceId()
        + ", flowNodeId="
        + this.getFlowNodeId()
        + ", flowNodeType="
        + this.getFlowNodeType()
        + ", processInstanceId="
        + this.getProcessInstanceId()
        + ", totalDurationInMs="
        + this.getTotalDurationInMs()
        + ", startDate="
        + this.getStartDate()
        + ", endDate="
        + this.getEndDate()
        + ", canceled="
        + this.getCanceled()
        + ", definitionKey="
        + this.getDefinitionKey()
        + ", definitionVersion="
        + this.getDefinitionVersion()
        + ", tenantId="
        + this.getTenantId()
        + ", engine="
        + this.getEngine()
        + ", userTaskInstanceId="
        + this.getUserTaskInstanceId()
        + ", dueDate="
        + this.getDueDate()
        + ", deleteReason="
        + this.getDeleteReason()
        + ", assignee="
        + this.getAssignee()
        + ", candidateGroups="
        + this.getCandidateGroups()
        + ", assigneeOperations="
        + this.getAssigneeOperations()
        + ", candidateGroupOperations="
        + this.getCandidateGroupOperations()
        + ", idleDurationInMs="
        + this.getIdleDurationInMs()
        + ", workDurationInMs="
        + this.getWorkDurationInMs()
        + ")";
  }

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
