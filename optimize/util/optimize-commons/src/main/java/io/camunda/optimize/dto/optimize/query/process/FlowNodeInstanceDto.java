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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Accessors;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Accessors(chain = true)
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
      @NonNull final String definitionKey,
      @NonNull final String definitionVersion,
      final String tenantId,
      @NonNull final String engine,
      @NonNull final String processInstanceId,
      @NonNull final String flowNodeId,
      @NonNull final String flowNodeType,
      @NonNull final String flowNodeInstanceId,
      final String userTaskInstanceId) {
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
      @NonNull final String definitionKey,
      @NonNull final String definitionVersion,
      final String tenantId,
      @NonNull final String processInstanceId,
      @NonNull final String flowNodeId,
      @NonNull final String flowNodeType,
      @NonNull final String flowNodeInstanceId) {
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
      @NonNull final String definitionKey,
      @NonNull final String engine,
      @NonNull final String processInstanceId,
      @NonNull final String flowNodeId,
      @NonNull final String flowNodeInstanceId,
      @NonNull final String userTaskInstanceId) {
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
      @NonNull final String definitionKey,
      @NonNull final String engine,
      @NonNull final String processInstanceId,
      @NonNull final String userTaskInstanceId) {
    this.processInstanceId = processInstanceId;
    this.definitionKey = definitionKey;
    this.engine = engine;
    flowNodeType = FLOW_NODE_TYPE_USER_TASK;
    this.userTaskInstanceId = userTaskInstanceId;
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
