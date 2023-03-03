/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@Schema(name = "FlowNodeStatistics")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowNodeStatistics {

  public static final String ACTIVITY_ID = "activityId";

  @Schema(description = "The id of the flow node for which the results are aggregated")
  private String activityId;
  @Schema(description = "The total number of active instances of the flow node")
  private Long active = 0L;
  @Schema(description = "The total number of canceled instances of the flow node")
  private Long canceled = 0L;
  @Schema(description = "The total number of incidents for the flow node")
  private Long incidents = 0L;
  @Schema(description = "The total number of completed instances of the flow node")
  private Long completed = 0L;

  public FlowNodeStatistics() {
  }

  public String getActivityId() {
    return activityId;
  }

  public FlowNodeStatistics setActivityId(String activityId) {
    this.activityId = activityId;
    return this;
  }

  public Long getActive() {
    return active;
  }

  public FlowNodeStatistics setActive(Long active) {
    this.active = active;
    return this;
  }

  public Long getCanceled() {
    return canceled;
  }

  public FlowNodeStatistics setCanceled(Long canceled) {
    this.canceled = canceled;
    return this;
  }

  public Long getIncidents() {
    return incidents;
  }

  public FlowNodeStatistics setIncidents(Long incidents) {
    this.incidents = incidents;
    return this;
  }

  public Long getCompleted() {
    return completed;
  }

  public FlowNodeStatistics setCompleted(Long completed) {
    this.completed = completed;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    FlowNodeStatistics that = (FlowNodeStatistics) o;
    return Objects.equals(activityId, that.activityId) && Objects.equals(active, that.active) && Objects.equals(canceled, that.canceled) && Objects.equals(
        incidents, that.incidents) && Objects.equals(completed, that.completed);
  }

  @Override
  public int hashCode() {
    return Objects.hash(activityId, active, canceled, incidents, completed);
  }

  @Override
  public String toString() {
    return "FlowNodeStatistics{" + "activityId='" + activityId + '\'' + ", active=" + active + ", canceled=" + canceled + ", incidents=" + incidents
        + ", completed=" + completed + '}';
  }
}
