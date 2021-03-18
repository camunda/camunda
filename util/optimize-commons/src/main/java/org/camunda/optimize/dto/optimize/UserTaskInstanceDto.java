/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.persistence.AssigneeOperationDto;
import org.camunda.optimize.dto.optimize.persistence.CandidateGroupOperationDto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
@FieldNameConstants
public class UserTaskInstanceDto implements OptimizeDto {

  private String id;

  @JsonIgnore
  private String processInstanceId;
  @JsonIgnore
  private String processDefinitionKey;
  @JsonIgnore
  private String engine;

  private String activityId;
  private String activityInstanceId;

  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private OffsetDateTime dueDate;
  private OffsetDateTime claimDate;

  private String deleteReason;
  private Boolean canceled;

  private String assignee;
  private List<String> candidateGroups = new ArrayList<>();
  private List<AssigneeOperationDto> assigneeOperations = new ArrayList<>();
  private List<CandidateGroupOperationDto> candidateGroupOperations = new ArrayList<>();

  private Long totalDurationInMs;
  private Long idleDurationInMs;
  private Long workDurationInMs;

  public UserTaskInstanceDto(final String id, final String processInstanceId, final String processDefinitionKey,
                             final String engine, final String assignee, final List<String> candidateGroups,
                             final List<AssigneeOperationDto> assigneeOperations,
                             final List<CandidateGroupOperationDto> candidateGroupOperations) {
    this.id = id;
    this.processInstanceId = processInstanceId;
    this.processDefinitionKey = processDefinitionKey;
    this.engine = engine;
    this.assignee = assignee;
    this.candidateGroups = candidateGroups;
    this.assigneeOperations = assigneeOperations;
    this.candidateGroupOperations = candidateGroupOperations;
  }

  public UserTaskInstanceDto(final String id, final String processInstanceId, final String processDefinitionKey,
                             final String engine, final String activityId, final String activityInstanceId,
                             final OffsetDateTime startDate, final OffsetDateTime endDate, final OffsetDateTime dueDate,
                             final String deleteReason, final Long totalDurationInMs) {
    this.id = id;
    this.processInstanceId = processInstanceId;
    this.processDefinitionKey = processDefinitionKey;
    this.engine = engine;
    this.activityId = activityId;
    this.activityInstanceId = activityInstanceId;
    this.startDate = startDate;
    this.endDate = endDate;
    this.dueDate = dueDate;
    this.deleteReason = deleteReason;
    this.totalDurationInMs = totalDurationInMs;
  }

}
