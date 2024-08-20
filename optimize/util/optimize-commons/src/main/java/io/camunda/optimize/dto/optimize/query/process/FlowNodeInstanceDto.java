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

  public FlowNodeInstanceDto setFlowNodeInstanceId(final String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    return this;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public FlowNodeInstanceDto setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public String getFlowNodeType() {
    return flowNodeType;
  }

  public FlowNodeInstanceDto setFlowNodeType(final String flowNodeType) {
    this.flowNodeType = flowNodeType;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public FlowNodeInstanceDto setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public Long getTotalDurationInMs() {
    return totalDurationInMs;
  }

  public FlowNodeInstanceDto setTotalDurationInMs(final Long totalDurationInMs) {
    this.totalDurationInMs = totalDurationInMs;
    return this;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public FlowNodeInstanceDto setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public FlowNodeInstanceDto setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  public Boolean getCanceled() {
    return canceled;
  }

  public FlowNodeInstanceDto setCanceled(final Boolean canceled) {
    this.canceled = canceled;
    return this;
  }

  public String getDefinitionKey() {
    return definitionKey;
  }

  public FlowNodeInstanceDto setDefinitionKey(final String definitionKey) {
    this.definitionKey = definitionKey;
    return this;
  }

  public String getDefinitionVersion() {
    return definitionVersion;
  }

  public FlowNodeInstanceDto setDefinitionVersion(final String definitionVersion) {
    this.definitionVersion = definitionVersion;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public FlowNodeInstanceDto setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getEngine() {
    return engine;
  }

  @JsonIgnore
  public FlowNodeInstanceDto setEngine(final String engine) {
    this.engine = engine;
    return this;
  }

  public String getUserTaskInstanceId() {
    return userTaskInstanceId;
  }

  public FlowNodeInstanceDto setUserTaskInstanceId(final String userTaskInstanceId) {
    this.userTaskInstanceId = userTaskInstanceId;
    return this;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public FlowNodeInstanceDto setDueDate(final OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public String getDeleteReason() {
    return deleteReason;
  }

  public FlowNodeInstanceDto setDeleteReason(final String deleteReason) {
    this.deleteReason = deleteReason;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public FlowNodeInstanceDto setAssignee(final String assignee) {
    this.assignee = assignee;
    return this;
  }

  public List<String> getCandidateGroups() {
    return candidateGroups;
  }

  public FlowNodeInstanceDto setCandidateGroups(final List<String> candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public List<AssigneeOperationDto> getAssigneeOperations() {
    return assigneeOperations;
  }

  public FlowNodeInstanceDto setAssigneeOperations(
      final List<AssigneeOperationDto> assigneeOperations) {
    this.assigneeOperations = assigneeOperations;
    return this;
  }

  public List<CandidateGroupOperationDto> getCandidateGroupOperations() {
    return candidateGroupOperations;
  }

  public FlowNodeInstanceDto setCandidateGroupOperations(
      final List<CandidateGroupOperationDto> candidateGroupOperations) {
    this.candidateGroupOperations = candidateGroupOperations;
    return this;
  }

  public Long getIdleDurationInMs() {
    return idleDurationInMs;
  }

  public FlowNodeInstanceDto setIdleDurationInMs(final Long idleDurationInMs) {
    this.idleDurationInMs = idleDurationInMs;
    return this;
  }

  public Long getWorkDurationInMs() {
    return workDurationInMs;
  }

  public FlowNodeInstanceDto setWorkDurationInMs(final Long workDurationInMs) {
    this.workDurationInMs = workDurationInMs;
    return this;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof FlowNodeInstanceDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $flowNodeInstanceId = getFlowNodeInstanceId();
    result = result * PRIME + ($flowNodeInstanceId == null ? 43 : $flowNodeInstanceId.hashCode());
    final Object $flowNodeId = getFlowNodeId();
    result = result * PRIME + ($flowNodeId == null ? 43 : $flowNodeId.hashCode());
    final Object $flowNodeType = getFlowNodeType();
    result = result * PRIME + ($flowNodeType == null ? 43 : $flowNodeType.hashCode());
    final Object $processInstanceId = getProcessInstanceId();
    result = result * PRIME + ($processInstanceId == null ? 43 : $processInstanceId.hashCode());
    final Object $totalDurationInMs = getTotalDurationInMs();
    result = result * PRIME + ($totalDurationInMs == null ? 43 : $totalDurationInMs.hashCode());
    final Object $startDate = getStartDate();
    result = result * PRIME + ($startDate == null ? 43 : $startDate.hashCode());
    final Object $endDate = getEndDate();
    result = result * PRIME + ($endDate == null ? 43 : $endDate.hashCode());
    final Object $canceled = getCanceled();
    result = result * PRIME + ($canceled == null ? 43 : $canceled.hashCode());
    final Object $definitionKey = getDefinitionKey();
    result = result * PRIME + ($definitionKey == null ? 43 : $definitionKey.hashCode());
    final Object $definitionVersion = getDefinitionVersion();
    result = result * PRIME + ($definitionVersion == null ? 43 : $definitionVersion.hashCode());
    final Object $tenantId = getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    final Object $engine = getEngine();
    result = result * PRIME + ($engine == null ? 43 : $engine.hashCode());
    final Object $userTaskInstanceId = getUserTaskInstanceId();
    result = result * PRIME + ($userTaskInstanceId == null ? 43 : $userTaskInstanceId.hashCode());
    final Object $dueDate = getDueDate();
    result = result * PRIME + ($dueDate == null ? 43 : $dueDate.hashCode());
    final Object $deleteReason = getDeleteReason();
    result = result * PRIME + ($deleteReason == null ? 43 : $deleteReason.hashCode());
    final Object $assignee = getAssignee();
    result = result * PRIME + ($assignee == null ? 43 : $assignee.hashCode());
    final Object $candidateGroups = getCandidateGroups();
    result = result * PRIME + ($candidateGroups == null ? 43 : $candidateGroups.hashCode());
    final Object $assigneeOperations = getAssigneeOperations();
    result = result * PRIME + ($assigneeOperations == null ? 43 : $assigneeOperations.hashCode());
    final Object $candidateGroupOperations = getCandidateGroupOperations();
    result =
        result * PRIME
            + ($candidateGroupOperations == null ? 43 : $candidateGroupOperations.hashCode());
    final Object $idleDurationInMs = getIdleDurationInMs();
    result = result * PRIME + ($idleDurationInMs == null ? 43 : $idleDurationInMs.hashCode());
    final Object $workDurationInMs = getWorkDurationInMs();
    result = result * PRIME + ($workDurationInMs == null ? 43 : $workDurationInMs.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof FlowNodeInstanceDto)) {
      return false;
    }
    final FlowNodeInstanceDto other = (FlowNodeInstanceDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$flowNodeInstanceId = getFlowNodeInstanceId();
    final Object other$flowNodeInstanceId = other.getFlowNodeInstanceId();
    if (this$flowNodeInstanceId == null
        ? other$flowNodeInstanceId != null
        : !this$flowNodeInstanceId.equals(other$flowNodeInstanceId)) {
      return false;
    }
    final Object this$flowNodeId = getFlowNodeId();
    final Object other$flowNodeId = other.getFlowNodeId();
    if (this$flowNodeId == null
        ? other$flowNodeId != null
        : !this$flowNodeId.equals(other$flowNodeId)) {
      return false;
    }
    final Object this$flowNodeType = getFlowNodeType();
    final Object other$flowNodeType = other.getFlowNodeType();
    if (this$flowNodeType == null
        ? other$flowNodeType != null
        : !this$flowNodeType.equals(other$flowNodeType)) {
      return false;
    }
    final Object this$processInstanceId = getProcessInstanceId();
    final Object other$processInstanceId = other.getProcessInstanceId();
    if (this$processInstanceId == null
        ? other$processInstanceId != null
        : !this$processInstanceId.equals(other$processInstanceId)) {
      return false;
    }
    final Object this$totalDurationInMs = getTotalDurationInMs();
    final Object other$totalDurationInMs = other.getTotalDurationInMs();
    if (this$totalDurationInMs == null
        ? other$totalDurationInMs != null
        : !this$totalDurationInMs.equals(other$totalDurationInMs)) {
      return false;
    }
    final Object this$startDate = getStartDate();
    final Object other$startDate = other.getStartDate();
    if (this$startDate == null
        ? other$startDate != null
        : !this$startDate.equals(other$startDate)) {
      return false;
    }
    final Object this$endDate = getEndDate();
    final Object other$endDate = other.getEndDate();
    if (this$endDate == null ? other$endDate != null : !this$endDate.equals(other$endDate)) {
      return false;
    }
    final Object this$canceled = getCanceled();
    final Object other$canceled = other.getCanceled();
    if (this$canceled == null ? other$canceled != null : !this$canceled.equals(other$canceled)) {
      return false;
    }
    final Object this$definitionKey = getDefinitionKey();
    final Object other$definitionKey = other.getDefinitionKey();
    if (this$definitionKey == null
        ? other$definitionKey != null
        : !this$definitionKey.equals(other$definitionKey)) {
      return false;
    }
    final Object this$definitionVersion = getDefinitionVersion();
    final Object other$definitionVersion = other.getDefinitionVersion();
    if (this$definitionVersion == null
        ? other$definitionVersion != null
        : !this$definitionVersion.equals(other$definitionVersion)) {
      return false;
    }
    final Object this$tenantId = getTenantId();
    final Object other$tenantId = other.getTenantId();
    if (this$tenantId == null ? other$tenantId != null : !this$tenantId.equals(other$tenantId)) {
      return false;
    }
    final Object this$engine = getEngine();
    final Object other$engine = other.getEngine();
    if (this$engine == null ? other$engine != null : !this$engine.equals(other$engine)) {
      return false;
    }
    final Object this$userTaskInstanceId = getUserTaskInstanceId();
    final Object other$userTaskInstanceId = other.getUserTaskInstanceId();
    if (this$userTaskInstanceId == null
        ? other$userTaskInstanceId != null
        : !this$userTaskInstanceId.equals(other$userTaskInstanceId)) {
      return false;
    }
    final Object this$dueDate = getDueDate();
    final Object other$dueDate = other.getDueDate();
    if (this$dueDate == null ? other$dueDate != null : !this$dueDate.equals(other$dueDate)) {
      return false;
    }
    final Object this$deleteReason = getDeleteReason();
    final Object other$deleteReason = other.getDeleteReason();
    if (this$deleteReason == null
        ? other$deleteReason != null
        : !this$deleteReason.equals(other$deleteReason)) {
      return false;
    }
    final Object this$assignee = getAssignee();
    final Object other$assignee = other.getAssignee();
    if (this$assignee == null ? other$assignee != null : !this$assignee.equals(other$assignee)) {
      return false;
    }
    final Object this$candidateGroups = getCandidateGroups();
    final Object other$candidateGroups = other.getCandidateGroups();
    if (this$candidateGroups == null
        ? other$candidateGroups != null
        : !this$candidateGroups.equals(other$candidateGroups)) {
      return false;
    }
    final Object this$assigneeOperations = getAssigneeOperations();
    final Object other$assigneeOperations = other.getAssigneeOperations();
    if (this$assigneeOperations == null
        ? other$assigneeOperations != null
        : !this$assigneeOperations.equals(other$assigneeOperations)) {
      return false;
    }
    final Object this$candidateGroupOperations = getCandidateGroupOperations();
    final Object other$candidateGroupOperations = other.getCandidateGroupOperations();
    if (this$candidateGroupOperations == null
        ? other$candidateGroupOperations != null
        : !this$candidateGroupOperations.equals(other$candidateGroupOperations)) {
      return false;
    }
    final Object this$idleDurationInMs = getIdleDurationInMs();
    final Object other$idleDurationInMs = other.getIdleDurationInMs();
    if (this$idleDurationInMs == null
        ? other$idleDurationInMs != null
        : !this$idleDurationInMs.equals(other$idleDurationInMs)) {
      return false;
    }
    final Object this$workDurationInMs = getWorkDurationInMs();
    final Object other$workDurationInMs = other.getWorkDurationInMs();
    if (this$workDurationInMs == null
        ? other$workDurationInMs != null
        : !this$workDurationInMs.equals(other$workDurationInMs)) {
      return false;
    }
    return true;
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
