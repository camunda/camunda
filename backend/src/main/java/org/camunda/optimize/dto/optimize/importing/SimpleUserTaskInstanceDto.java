/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SimpleUserTaskInstanceDto implements OptimizeDto {

  private String id;

  private String activityId;
  private String activityInstanceId;

  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private OffsetDateTime dueDate;

  private String deleteReason;

  private Long totalDurationInMs;
  private Long idleDurationInMs;
  private Long workDurationInMs;

  private Set<UserOperationDto> userOperations = new HashSet<>();

  protected SimpleUserTaskInstanceDto() {
  }

  public SimpleUserTaskInstanceDto(final String id,
                                   final String activityId, final String activityInstanceId, final OffsetDateTime startDate,
                                   final OffsetDateTime endDate, final OffsetDateTime dueDate, final String deleteReason,
                                   final Long totalDurationInMs) {
    this(
      id, activityId, activityInstanceId, startDate, endDate, dueDate, deleteReason, totalDurationInMs, Collections.emptySet()
    );
  }

  public SimpleUserTaskInstanceDto(final String id, final Set<UserOperationDto> userOperations) {
    this(id, null, null, null, null, null, null, null, userOperations);
  }

  public SimpleUserTaskInstanceDto(final String id,
                                   final String activityId, final String activityInstanceId, final OffsetDateTime startDate,
                                   final OffsetDateTime endDate, final OffsetDateTime dueDate, final String deleteReason,
                                   final Long totalDurationInMs, final Set<UserOperationDto> userOperations) {
    this.id = id;
    this.activityId = activityId;
    this.activityInstanceId = activityInstanceId;
    this.startDate = startDate;
    this.endDate = endDate;
    this.dueDate = dueDate;
    this.deleteReason = deleteReason;
    this.totalDurationInMs = totalDurationInMs;
    this.userOperations.addAll(userOperations);
  }

  public String getId() {
    return id;
  }

  public String getActivityId() {
    return activityId;
  }

  public String getActivityInstanceId() {
    return activityInstanceId;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public String getDeleteReason() {
    return deleteReason;
  }

  public Long getTotalDurationInMs() {
    return totalDurationInMs;
  }

  public Long getIdleDurationInMs() {
    return idleDurationInMs;
  }

  public Long getWorkDurationInMs() {
    return workDurationInMs;
  }

  public Set<UserOperationDto> getUserOperations() {
    return userOperations;
  }
}
