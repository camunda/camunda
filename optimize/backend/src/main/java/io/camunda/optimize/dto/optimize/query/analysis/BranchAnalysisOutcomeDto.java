/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.analysis;

import java.util.Objects;

public class BranchAnalysisOutcomeDto {

  protected Long activitiesReached;
  protected Long activityCount;
  protected String activityId;

  public BranchAnalysisOutcomeDto() {}

  public Long getActivitiesReached() {
    return activitiesReached;
  }

  public void setActivitiesReached(final Long activitiesReached) {
    this.activitiesReached = activitiesReached;
  }

  public Long getActivityCount() {
    return activityCount;
  }

  public void setActivityCount(final Long activityCount) {
    this.activityCount = activityCount;
  }

  public String getActivityId() {
    return activityId;
  }

  public void setActivityId(final String activityId) {
    this.activityId = activityId;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof BranchAnalysisOutcomeDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(activitiesReached, activityCount, activityId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BranchAnalysisOutcomeDto that = (BranchAnalysisOutcomeDto) o;
    return Objects.equals(activitiesReached, that.activitiesReached)
        && Objects.equals(activityCount, that.activityCount)
        && Objects.equals(activityId, that.activityId);
  }

  @Override
  public String toString() {
    return "BranchAnalysisOutcomeDto(activitiesReached="
        + getActivitiesReached()
        + ", activityCount="
        + getActivityCount()
        + ", activityId="
        + getActivityId()
        + ")";
  }
}
