/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.event.process;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.persistence.AssigneeOperationDto;
import org.camunda.optimize.dto.optimize.persistence.CandidateGroupOperationDto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@FieldNameConstants
@EqualsAndHashCode
public class FlowNodeInstanceDto implements Serializable, OptimizeDto {

  private String flowNodeInstanceId;
  private String flowNodeId;
  private String flowNodeType;
  private String processInstanceId;
  private Long totalDurationInMs;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private Boolean canceled;

  // UserTask specific fields.
  // Note that processDefinitionKey and engine are transient and only used for correct "routing" during import
  @JsonIgnore
  private String processDefinitionKey;
  @JsonIgnore
  private String engine;

  private String userTaskInstanceId;

  private OffsetDateTime dueDate;

  private String deleteReason;

  private String assignee;
  @Builder.Default
  private List<String> candidateGroups = new ArrayList<>();
  @Builder.Default
  private List<AssigneeOperationDto> assigneeOperations = new ArrayList<>();
  @Builder.Default
  private List<CandidateGroupOperationDto> candidateGroupOperations = new ArrayList<>();

  private Long idleDurationInMs;
  private Long workDurationInMs;

}
