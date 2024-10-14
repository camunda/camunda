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
    final Object this$flowNodeInstanceId = this.getFlowNodeInstanceId();
    final Object other$flowNodeInstanceId = other.getFlowNodeInstanceId();
    if (this$flowNodeInstanceId == null
        ? other$flowNodeInstanceId != null
        : !this$flowNodeInstanceId.equals(other$flowNodeInstanceId)) {
      return false;
    }
    final Object this$flowNodeId = this.getFlowNodeId();
    final Object other$flowNodeId = other.getFlowNodeId();
    if (this$flowNodeId == null
        ? other$flowNodeId != null
        : !this$flowNodeId.equals(other$flowNodeId)) {
      return false;
    }
    final Object this$flowNodeType = this.getFlowNodeType();
    final Object other$flowNodeType = other.getFlowNodeType();
    if (this$flowNodeType == null
        ? other$flowNodeType != null
        : !this$flowNodeType.equals(other$flowNodeType)) {
      return false;
    }
    final Object this$processInstanceId = this.getProcessInstanceId();
    final Object other$processInstanceId = other.getProcessInstanceId();
    if (this$processInstanceId == null
        ? other$processInstanceId != null
        : !this$processInstanceId.equals(other$processInstanceId)) {
      return false;
    }
    final Object this$totalDurationInMs = this.getTotalDurationInMs();
    final Object other$totalDurationInMs = other.getTotalDurationInMs();
    if (this$totalDurationInMs == null
        ? other$totalDurationInMs != null
        : !this$totalDurationInMs.equals(other$totalDurationInMs)) {
      return false;
    }
    final Object this$startDate = this.getStartDate();
    final Object other$startDate = other.getStartDate();
    if (this$startDate == null
        ? other$startDate != null
        : !this$startDate.equals(other$startDate)) {
      return false;
    }
    final Object this$endDate = this.getEndDate();
    final Object other$endDate = other.getEndDate();
    if (this$endDate == null ? other$endDate != null : !this$endDate.equals(other$endDate)) {
      return false;
    }
    final Object this$canceled = this.getCanceled();
    final Object other$canceled = other.getCanceled();
    if (this$canceled == null ? other$canceled != null : !this$canceled.equals(other$canceled)) {
      return false;
    }
    final Object this$definitionKey = this.getDefinitionKey();
    final Object other$definitionKey = other.getDefinitionKey();
    if (this$definitionKey == null
        ? other$definitionKey != null
        : !this$definitionKey.equals(other$definitionKey)) {
      return false;
    }
    final Object this$definitionVersion = this.getDefinitionVersion();
    final Object other$definitionVersion = other.getDefinitionVersion();
    if (this$definitionVersion == null
        ? other$definitionVersion != null
        : !this$definitionVersion.equals(other$definitionVersion)) {
      return false;
    }
    final Object this$tenantId = this.getTenantId();
    final Object other$tenantId = other.getTenantId();
    if (this$tenantId == null ? other$tenantId != null : !this$tenantId.equals(other$tenantId)) {
      return false;
    }
    final Object this$engine = this.getEngine();
    final Object other$engine = other.getEngine();
    if (this$engine == null ? other$engine != null : !this$engine.equals(other$engine)) {
      return false;
    }
    final Object this$userTaskInstanceId = this.getUserTaskInstanceId();
    final Object other$userTaskInstanceId = other.getUserTaskInstanceId();
    if (this$userTaskInstanceId == null
        ? other$userTaskInstanceId != null
        : !this$userTaskInstanceId.equals(other$userTaskInstanceId)) {
      return false;
    }
    final Object this$dueDate = this.getDueDate();
    final Object other$dueDate = other.getDueDate();
    if (this$dueDate == null ? other$dueDate != null : !this$dueDate.equals(other$dueDate)) {
      return false;
    }
    final Object this$deleteReason = this.getDeleteReason();
    final Object other$deleteReason = other.getDeleteReason();
    if (this$deleteReason == null
        ? other$deleteReason != null
        : !this$deleteReason.equals(other$deleteReason)) {
      return false;
    }
    final Object this$assignee = this.getAssignee();
    final Object other$assignee = other.getAssignee();
    if (this$assignee == null ? other$assignee != null : !this$assignee.equals(other$assignee)) {
      return false;
    }
    final Object this$candidateGroups = this.getCandidateGroups();
    final Object other$candidateGroups = other.getCandidateGroups();
    if (this$candidateGroups == null
        ? other$candidateGroups != null
        : !this$candidateGroups.equals(other$candidateGroups)) {
      return false;
    }
    final Object this$assigneeOperations = this.getAssigneeOperations();
    final Object other$assigneeOperations = other.getAssigneeOperations();
    if (this$assigneeOperations == null
        ? other$assigneeOperations != null
        : !this$assigneeOperations.equals(other$assigneeOperations)) {
      return false;
    }
    final Object this$candidateGroupOperations = this.getCandidateGroupOperations();
    final Object other$candidateGroupOperations = other.getCandidateGroupOperations();
    if (this$candidateGroupOperations == null
        ? other$candidateGroupOperations != null
        : !this$candidateGroupOperations.equals(other$candidateGroupOperations)) {
      return false;
    }
    final Object this$idleDurationInMs = this.getIdleDurationInMs();
    final Object other$idleDurationInMs = other.getIdleDurationInMs();
    if (this$idleDurationInMs == null
        ? other$idleDurationInMs != null
        : !this$idleDurationInMs.equals(other$idleDurationInMs)) {
      return false;
    }
    final Object this$workDurationInMs = this.getWorkDurationInMs();
    final Object other$workDurationInMs = other.getWorkDurationInMs();
    if (this$workDurationInMs == null
        ? other$workDurationInMs != null
        : !this$workDurationInMs.equals(other$workDurationInMs)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof FlowNodeInstanceDto;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $flowNodeInstanceId = this.getFlowNodeInstanceId();
    result = result * PRIME + ($flowNodeInstanceId == null ? 43 : $flowNodeInstanceId.hashCode());
    final Object $flowNodeId = this.getFlowNodeId();
    result = result * PRIME + ($flowNodeId == null ? 43 : $flowNodeId.hashCode());
    final Object $flowNodeType = this.getFlowNodeType();
    result = result * PRIME + ($flowNodeType == null ? 43 : $flowNodeType.hashCode());
    final Object $processInstanceId = this.getProcessInstanceId();
    result = result * PRIME + ($processInstanceId == null ? 43 : $processInstanceId.hashCode());
    final Object $totalDurationInMs = this.getTotalDurationInMs();
    result = result * PRIME + ($totalDurationInMs == null ? 43 : $totalDurationInMs.hashCode());
    final Object $startDate = this.getStartDate();
    result = result * PRIME + ($startDate == null ? 43 : $startDate.hashCode());
    final Object $endDate = this.getEndDate();
    result = result * PRIME + ($endDate == null ? 43 : $endDate.hashCode());
    final Object $canceled = this.getCanceled();
    result = result * PRIME + ($canceled == null ? 43 : $canceled.hashCode());
    final Object $definitionKey = this.getDefinitionKey();
    result = result * PRIME + ($definitionKey == null ? 43 : $definitionKey.hashCode());
    final Object $definitionVersion = this.getDefinitionVersion();
    result = result * PRIME + ($definitionVersion == null ? 43 : $definitionVersion.hashCode());
    final Object $tenantId = this.getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    final Object $engine = this.getEngine();
    result = result * PRIME + ($engine == null ? 43 : $engine.hashCode());
    final Object $userTaskInstanceId = this.getUserTaskInstanceId();
    result = result * PRIME + ($userTaskInstanceId == null ? 43 : $userTaskInstanceId.hashCode());
    final Object $dueDate = this.getDueDate();
    result = result * PRIME + ($dueDate == null ? 43 : $dueDate.hashCode());
    final Object $deleteReason = this.getDeleteReason();
    result = result * PRIME + ($deleteReason == null ? 43 : $deleteReason.hashCode());
    final Object $assignee = this.getAssignee();
    result = result * PRIME + ($assignee == null ? 43 : $assignee.hashCode());
    final Object $candidateGroups = this.getCandidateGroups();
    result = result * PRIME + ($candidateGroups == null ? 43 : $candidateGroups.hashCode());
    final Object $assigneeOperations = this.getAssigneeOperations();
    result = result * PRIME + ($assigneeOperations == null ? 43 : $assigneeOperations.hashCode());
    final Object $candidateGroupOperations = this.getCandidateGroupOperations();
    result =
        result * PRIME
            + ($candidateGroupOperations == null ? 43 : $candidateGroupOperations.hashCode());
    final Object $idleDurationInMs = this.getIdleDurationInMs();
    result = result * PRIME + ($idleDurationInMs == null ? 43 : $idleDurationInMs.hashCode());
    final Object $workDurationInMs = this.getWorkDurationInMs();
    result = result * PRIME + ($workDurationInMs == null ? 43 : $workDurationInMs.hashCode());
    return result;
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
