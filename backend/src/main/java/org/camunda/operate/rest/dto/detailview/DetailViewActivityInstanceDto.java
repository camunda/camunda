package org.camunda.operate.rest.dto.detailview;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.entities.detailview.ActivityInstanceForDetailViewEntity;

public class DetailViewActivityInstanceDto {

  private String id;

  private ActivityType type;

  private ActivityState state;

  private String activityId;

  private OffsetDateTime startDate;

  private OffsetDateTime endDate;

  private String parentId;

  private List<DetailViewActivityInstanceDto> children = new ArrayList<>();

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

  public ActivityType getType() {
    return type;
  }

  public void setType(ActivityType type) {
    this.type = type;
  }

  public String getParentId() {
    return parentId;
  }

  public void setParentId(String parentId) {
    this.parentId = parentId;
  }

  public List<DetailViewActivityInstanceDto> getChildren() {
    return children;
  }

  public void setChildren(List<DetailViewActivityInstanceDto> children) {
    this.children = children;
  }

  public static DetailViewActivityInstanceDto createFrom(ActivityInstanceForDetailViewEntity activityInstanceEntity) {
    if (activityInstanceEntity == null) {
      return null;
    }
    DetailViewActivityInstanceDto activity = new DetailViewActivityInstanceDto();
    activity.setId(activityInstanceEntity.getId());
    activity.setActivityId(activityInstanceEntity.getActivityId());
    activity.setStartDate(activityInstanceEntity.getStartDate());
    activity.setEndDate(activityInstanceEntity.getEndDate());
    if (activityInstanceEntity.getIncidentKey() != null) {
      activity.setState(ActivityState.INCIDENT);
    } else {
      activity.setState(activityInstanceEntity.getState());
    }
    activity.setType(activityInstanceEntity.getType());
    activity.setParentId(activityInstanceEntity.getScopeId());
    return activity;
  }

  public static List<DetailViewActivityInstanceDto> createFrom(List<ActivityInstanceForDetailViewEntity> activityInstanceEntities) {
    List<DetailViewActivityInstanceDto> result = new ArrayList<>();
    if (activityInstanceEntities != null) {
      for (ActivityInstanceForDetailViewEntity activityInstanceEntity: activityInstanceEntities) {
        if (activityInstanceEntity != null) {
          result.add(createFrom(activityInstanceEntity));
        }
      }
    }
    return result;
  }

  public static Map<String, DetailViewActivityInstanceDto> createMapFrom(List<ActivityInstanceForDetailViewEntity> activityInstanceEntities) {
    Map<String, DetailViewActivityInstanceDto> result = new LinkedHashMap<>();
    if (activityInstanceEntities != null) {
      for (ActivityInstanceForDetailViewEntity activityInstanceEntity: activityInstanceEntities) {
        if (activityInstanceEntity != null) {
          result.put(activityInstanceEntity.getId(), createFrom(activityInstanceEntity));
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

    DetailViewActivityInstanceDto that = (DetailViewActivityInstanceDto) o;

    if (id != null ? !id.equals(that.id) : that.id != null)
      return false;
    if (type != that.type)
      return false;
    if (state != that.state)
      return false;
    if (activityId != null ? !activityId.equals(that.activityId) : that.activityId != null)
      return false;
    if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null)
      return false;
    if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null)
      return false;
    if (parentId != null ? !parentId.equals(that.parentId) : that.parentId != null)
      return false;
    return children != null ? children.equals(that.children) : that.children == null;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (type != null ? type.hashCode() : 0);
    result = 31 * result + (state != null ? state.hashCode() : 0);
    result = 31 * result + (activityId != null ? activityId.hashCode() : 0);
    result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
    result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
    result = 31 * result + (parentId != null ? parentId.hashCode() : 0);
    result = 31 * result + (children != null ? children.hashCode() : 0);
    return result;
  }

}
