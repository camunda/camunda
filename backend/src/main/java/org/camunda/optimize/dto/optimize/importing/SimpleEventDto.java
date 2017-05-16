package org.camunda.optimize.dto.optimize.importing;

import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;

public class SimpleEventDto implements Serializable,OptimizeDto {

  protected String id;
  protected String activityId;
  protected String activityType;
  protected long durationInMs;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getActivityId() {
    return activityId;
  }

  public void setActivityId(String activityId) {
    this.activityId = activityId;
  }

  public String getActivityType() {
    return activityType;
  }

  public void setActivityType(String activityType) {
    this.activityType = activityType;
  }

  public long getDurationInMs() {
    return durationInMs;
  }

  public void setDurationInMs(long durationInMs) {
    this.durationInMs = durationInMs;
  }
}
