/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.process;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.persistence.AssigneeOperationDto;
import org.camunda.optimize.dto.optimize.persistence.CandidateGroupOperationDto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;

@NoArgsConstructor
@AllArgsConstructor
@Data
@FieldNameConstants
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
  @JsonIgnore
  private String engine;

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
  public FlowNodeInstanceDto(@NonNull final String definitionKey, @NonNull final String definitionVersion,
                             final String tenantId, @NonNull final String engine,
                             @NonNull final String processInstanceId, @NonNull final String flowNodeId,
                             @NonNull final String flowNodeType, @NonNull final String flowNodeInstanceId,
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

  // event/zeebe process activity instance specific ctor with mandatory fields
  public FlowNodeInstanceDto(@NonNull final String definitionKey, @NonNull final String definitionVersion,
                             final String tenantId,
                             @NonNull final String processInstanceId, @NonNull final String flowNodeId,
                             @NonNull final String flowNodeType, @NonNull final String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    this.flowNodeId = flowNodeId;
    this.flowNodeType = flowNodeType;
    this.processInstanceId = processInstanceId;
    this.definitionKey = definitionKey;
    this.definitionVersion = definitionVersion;
    this.tenantId = tenantId;
  }

  // engine user task specific ctor with mandatory fields
  public FlowNodeInstanceDto(@NonNull final String definitionKey, @NonNull final String engine,
                             @NonNull final String processInstanceId, @NonNull final String flowNodeId,
                             @NonNull final String flowNodeInstanceId, @NonNull final String userTaskInstanceId) {
    this.processInstanceId = processInstanceId;
    this.definitionKey = definitionKey;
    this.engine = engine;
    this.flowNodeId = flowNodeId;
    this.flowNodeInstanceId = flowNodeInstanceId;
    this.flowNodeType = FLOW_NODE_TYPE_USER_TASK;
    this.userTaskInstanceId = userTaskInstanceId;
  }

  // engine identity link log specific ctor with mandatory fields
  public FlowNodeInstanceDto(@NonNull final String definitionKey, @NonNull final String engine,
                             @NonNull final String processInstanceId, @NonNull final String userTaskInstanceId) {
    this.processInstanceId = processInstanceId;
    this.definitionKey = definitionKey;
    this.engine = engine;
    this.flowNodeType = FLOW_NODE_TYPE_USER_TASK;
    this.userTaskInstanceId = userTaskInstanceId;
  }

}
