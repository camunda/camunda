package org.camunda.optimize.dto.optimize;

import java.io.Serializable;

public class SimpleEventDto implements Serializable,OptimizeDto {

  String id;
  String activityId;
  String activityType;
  long durationInMs;

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
