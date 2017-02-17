package org.camunda.optimize.dto.optimize;

/**
 * @author Askar Akhmerov
 */
public class CorrelationOutcomeDto {
  private Long activitiesReached;
  private Long activityCount;
  private String activityId;

  public Long getActivitiesReached() {
    return activitiesReached;
  }

  public void setActivitiesReached(Long activitiesReached) {
    this.activitiesReached = activitiesReached;
  }

  public Long getActivityCount() {
    return activityCount;
  }

  public void setActivityCount(Long activityCount) {
    this.activityCount = activityCount;
  }

  public String getActivityId() {
    return activityId;
  }

  public void setActivityId(String activityId) {
    this.activityId = activityId;
  }
}
