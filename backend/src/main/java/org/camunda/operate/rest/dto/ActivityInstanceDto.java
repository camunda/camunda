package org.camunda.operate.rest.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;

public class ActivityInstanceDto {

  private String id;

  private ActivityState state;

  private String activityId;

  private OffsetDateTime startDate;

  private OffsetDateTime endDate;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public ActivityState getState() {
    return state;
  }

  public void setState(ActivityState state) {
    this.state = state;
  }

  public String getActivityId() {
    return activityId;
  }

  public void setActivityId(String activityId) {
    this.activityId = activityId;
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

  public static ActivityInstanceDto createFrom(ActivityInstanceEntity activityInstanceEntity) {
    if (activityInstanceEntity == null) {
      return null;
    }
    ActivityInstanceDto activity = new ActivityInstanceDto();
    activity.setId(activityInstanceEntity.getId());
    activity.setActivityId(activityInstanceEntity.getActivityId());
    activity.setStartDate(activityInstanceEntity.getStartDate());
    activity.setEndDate(activityInstanceEntity.getEndDate());
    activity.setState(activityInstanceEntity.getState());
    return activity;
  }

  public static List<ActivityInstanceDto> createFrom(List<ActivityInstanceEntity> activityInstanceEntities) {
    List<ActivityInstanceDto> result = new ArrayList<>();
    if (activityInstanceEntities != null) {
      for (ActivityInstanceEntity activityInstanceEntity: activityInstanceEntities) {
        if (activityInstanceEntity != null) {
          result.add(createFrom(activityInstanceEntity));
        }
      }
    }
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    ActivityInstanceDto that = (ActivityInstanceDto) o;

    if (id != null ? !id.equals(that.id) : that.id != null)
      return false;
    if (state != that.state)
      return false;
    if (activityId != null ? !activityId.equals(that.activityId) : that.activityId != null)
      return false;
    if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null)
      return false;
    return endDate != null ? endDate.equals(that.endDate) : that.endDate == null;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (state != null ? state.hashCode() : 0);
    result = 31 * result + (activityId != null ? activityId.hashCode() : 0);
    result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
    result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
    return result;
  }
}
