/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class UserTaskInstanceDto implements OptimizeDto {

  private String id;

  @JsonIgnore
  private String processInstanceId;
  @JsonIgnore
  private String engine;

  private String activityId;
  private String activityInstanceId;

  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private OffsetDateTime dueDate;
  private OffsetDateTime claimDate;

  private String deleteReason;

  private Long totalDurationInMs;
  private Long idleDurationInMs;
  private Long workDurationInMs;

  private Set<UserOperationDto> userOperations = new HashSet<>();

  public UserTaskInstanceDto(final String id, final String processInstanceId, final String engine,
                             final Set<UserOperationDto> userOperations) {
    this.id = id;
    this.processInstanceId = processInstanceId;
    this.engine = engine;
    this.userOperations = userOperations;
  }

  public UserTaskInstanceDto(final String id, final String processInstanceId, final String engine,
                             final String activityId, final String activityInstanceId, final OffsetDateTime startDate,
                             final OffsetDateTime endDate, final OffsetDateTime dueDate, final String deleteReason,
                             final Long totalDurationInMs) {
    this.id = id;
    this.processInstanceId = processInstanceId;
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
