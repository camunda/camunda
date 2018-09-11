package org.camunda.optimize.dto.optimize.importing;

import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Date;

public class SimpleEventDto implements Serializable,OptimizeDto {

  protected String id;
  protected String activityId;
  protected String activityType;
  protected long durationInMs;
  protected OffsetDateTime startDate;
  protected OffsetDateTime endDate;

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

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public void setStartDate(OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public void setEndDate(OffsetDateTime endDate) {
    this.endDate = endDate;
  }
}
