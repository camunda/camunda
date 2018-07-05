package org.camunda.operate.rest.dto;

import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;


public class IncidentDto {

  private String id;

  private String errorType;

  private String errorMessage;

  private IncidentState state;

  private String activityId;

  private String activityInstanceId;

  private String jobId;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getErrorType() {
    return errorType;
  }

  public void setErrorType(String errorType) {
    this.errorType = errorType;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public IncidentState getState() {
    return state;
  }

  public void setState(IncidentState state) {
    this.state = state;
  }

  public String getActivityId() {
    return activityId;
  }

  public void setActivityId(String activityId) {
    this.activityId = activityId;
  }

  public String getActivityInstanceId() {
    return activityInstanceId;
  }

  public void setActivityInstanceId(String activityInstanceId) {
    this.activityInstanceId = activityInstanceId;
  }

  public String getJobId() {
    return jobId;
  }

  public void setJobId(String jobId) {
    this.jobId = jobId;
  }

  public static IncidentDto createFrom(IncidentEntity incidentEntity) {
    if (incidentEntity == null) {
      return null;
    }
    IncidentDto incident = new IncidentDto();
    incident.setId(incidentEntity.getId());
    incident.setActivityId(incidentEntity.getActivityId());
    incident.setActivityInstanceId(incidentEntity.getActivityInstanceId());
    incident.setErrorMessage(incidentEntity.getErrorMessage());
    incident.setErrorType(incidentEntity.getErrorType());
    incident.setState(incidentEntity.getState());
    incident.setJobId(incidentEntity.getJobId());
    return incident;
  }

  public static List<IncidentDto> createFrom(List<IncidentEntity> incidentEntities) {
    List<IncidentDto> result = new ArrayList<>();
    if (incidentEntities != null) {
      for (IncidentEntity incidentEntity: incidentEntities) {
        if (incidentEntity != null) {
          result.add(createFrom(incidentEntity));
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

    IncidentDto that = (IncidentDto) o;

    if (id != null ? !id.equals(that.id) : that.id != null)
      return false;
    if (errorType != null ? !errorType.equals(that.errorType) : that.errorType != null)
      return false;
    if (errorMessage != null ? !errorMessage.equals(that.errorMessage) : that.errorMessage != null)
      return false;
    if (state != that.state)
      return false;
    if (activityId != null ? !activityId.equals(that.activityId) : that.activityId != null)
      return false;
    if (activityInstanceId != null ? !activityInstanceId.equals(that.activityInstanceId) : that.activityInstanceId != null)
      return false;
    return jobId != null ? jobId.equals(that.jobId) : that.jobId == null;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (errorType != null ? errorType.hashCode() : 0);
    result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
    result = 31 * result + (state != null ? state.hashCode() : 0);
    result = 31 * result + (activityId != null ? activityId.hashCode() : 0);
    result = 31 * result + (activityInstanceId != null ? activityInstanceId.hashCode() : 0);
    result = 31 * result + (jobId != null ? jobId.hashCode() : 0);
    return result;
  }
}
