/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate34To35.dtos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.persistence.AssigneeOperationDto;
import org.camunda.optimize.dto.optimize.persistence.CandidateGroupOperationDto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
@FieldNameConstants
public class UserTaskInstanceDtoOld implements OptimizeDto {
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

  private String deleteReason;
  private Boolean canceled;

  private String assignee;
  private List<String> candidateGroups = new ArrayList<>();
  private List<AssigneeOperationDto> assigneeOperations = new ArrayList<>();
  private List<CandidateGroupOperationDto> candidateGroupOperations = new ArrayList<>();

  private Long totalDurationInMs;
  private Long idleDurationInMs;
  private Long workDurationInMs;
}
