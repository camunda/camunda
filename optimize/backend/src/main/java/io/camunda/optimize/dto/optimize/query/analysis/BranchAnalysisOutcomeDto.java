/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.analysis;

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
    final int PRIME = 59;
    int result = 1;
    final Object $activitiesReached = getActivitiesReached();
    result = result * PRIME + ($activitiesReached == null ? 43 : $activitiesReached.hashCode());
    final Object $activityCount = getActivityCount();
    result = result * PRIME + ($activityCount == null ? 43 : $activityCount.hashCode());
    final Object $activityId = getActivityId();
    result = result * PRIME + ($activityId == null ? 43 : $activityId.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof BranchAnalysisOutcomeDto)) {
      return false;
    }
    final BranchAnalysisOutcomeDto other = (BranchAnalysisOutcomeDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$activitiesReached = getActivitiesReached();
    final Object other$activitiesReached = other.getActivitiesReached();
    if (this$activitiesReached == null
        ? other$activitiesReached != null
        : !this$activitiesReached.equals(other$activitiesReached)) {
      return false;
    }
    final Object this$activityCount = getActivityCount();
    final Object other$activityCount = other.getActivityCount();
    if (this$activityCount == null
        ? other$activityCount != null
        : !this$activityCount.equals(other$activityCount)) {
      return false;
    }
    final Object this$activityId = getActivityId();
    final Object other$activityId = other.getActivityId();
    if (this$activityId == null
        ? other$activityId != null
        : !this$activityId.equals(other$activityId)) {
      return false;
    }
    return true;
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
