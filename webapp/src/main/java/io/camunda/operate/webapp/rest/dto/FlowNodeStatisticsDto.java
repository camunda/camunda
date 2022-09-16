/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto;

public class FlowNodeStatisticsDto {

  private String activityId;

  private Long active = 0L;
  private Long canceled = 0L;
  private Long incidents = 0L;
  private Long completed = 0L;

  public FlowNodeStatisticsDto() {
  }

  public FlowNodeStatisticsDto(String activityId) {
    this.activityId = activityId;
  }

  public String getActivityId() {
    return activityId;
  }

  public FlowNodeStatisticsDto setActivityId(String activityId) {
    this.activityId = activityId;
    return this;
  }

  public Long getActive() {
    return active;
  }

  public FlowNodeStatisticsDto setActive(Long active) {
    this.active = active;
    return this;
  }

  public void addActive(Long active) {
    this.active += active;
  }

  public Long getCanceled() {
    return canceled;
  }

  public FlowNodeStatisticsDto setCanceled(Long canceled) {
    this.canceled = canceled;
    return this;
  }

  public void addCanceled(Long canceled) {
    this.canceled += canceled;
  }

  public Long getIncidents() {
    return incidents;
  }

  public FlowNodeStatisticsDto setIncidents(Long incidents) {
    this.incidents = incidents;
    return this;
  }

  public void addIncidents(Long incidents) {
    this.incidents += incidents;
  }

  public Long getCompleted() {
    return completed;
  }

  public FlowNodeStatisticsDto setCompleted(Long completed) {
    this.completed = completed;
    return this;
  }

  public void addCompleted(Long completed) {
    this.completed += completed;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    FlowNodeStatisticsDto that = (FlowNodeStatisticsDto) o;

    if (activityId != null ? !activityId.equals(that.activityId) : that.activityId != null)
      return false;
    if (active != null ? !active.equals(that.active) : that.active != null)
      return false;
    if (canceled != null ? !canceled.equals(that.canceled) : that.canceled != null)
      return false;
    if (incidents != null ? !incidents.equals(that.incidents) : that.incidents != null)
      return false;
    return completed != null ? completed.equals(that.completed) : that.completed == null;
  }

  @Override
  public int hashCode() {
    int result = activityId != null ? activityId.hashCode() : 0;
    result = 31 * result + (active != null ? active.hashCode() : 0);
    result = 31 * result + (canceled != null ? canceled.hashCode() : 0);
    result = 31 * result + (incidents != null ? incidents.hashCode() : 0);
    result = 31 * result + (completed != null ? completed.hashCode() : 0);
    return result;
  }

  @Override public String toString() {
    return "FlowNodeStatisticsDto{" + "activityId='" + activityId + '\'' + ", active=" + active + ", canceled="
        + canceled + ", incidents=" + incidents + ", completed=" + completed + '}';
  }
}
