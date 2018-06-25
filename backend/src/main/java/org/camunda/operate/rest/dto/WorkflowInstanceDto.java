package org.camunda.operate.rest.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;


public class WorkflowInstanceDto {

  private String id;

  private String workflowId;

  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  private WorkflowInstanceState state;

  private String businessKey;

  private List<IncidentDto> incidents = new ArrayList<>();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public void setWorkflowId(String workflowId) {
    this.workflowId = workflowId;
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

  public WorkflowInstanceState getState() {
    return state;
  }

  public void setState(WorkflowInstanceState state) {
    this.state = state;
  }

  public String getBusinessKey() {
    return businessKey;
  }

  public void setBusinessKey(String businessKey) {
    this.businessKey = businessKey;
  }

  public List<IncidentDto> getIncidents() {
    return incidents;
  }

  public void setIncidents(List<IncidentDto> incidents) {
    this.incidents = incidents;
  }

  public static WorkflowInstanceDto createFrom(WorkflowInstanceEntity workflowInstanceEntity) {
    if (workflowInstanceEntity == null) {
      return null;
    }
    WorkflowInstanceDto workflowInstance = new WorkflowInstanceDto();
    workflowInstance.setId(workflowInstanceEntity.getId());
    workflowInstance.setBusinessKey(workflowInstanceEntity.getBusinessKey());
    workflowInstance.setStartDate(workflowInstanceEntity.getStartDate());
    workflowInstance.setEndDate(workflowInstanceEntity.getEndDate());
    workflowInstance.setState(workflowInstanceEntity.getState());
    workflowInstance.setWorkflowId(workflowInstanceEntity.getWorkflowId());
    workflowInstance.setIncidents(IncidentDto.createFrom(workflowInstanceEntity.getIncidents()));
    return workflowInstance;
  }

  public static List<WorkflowInstanceDto> createFrom(List<WorkflowInstanceEntity> workflowInstanceEntities) {
    List<WorkflowInstanceDto> result = new ArrayList<>();
    if (workflowInstanceEntities != null) {
      for (WorkflowInstanceEntity workflowInstanceEntity: workflowInstanceEntities) {
        if (workflowInstanceEntity != null) {
          result.add(createFrom(workflowInstanceEntity));
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

    WorkflowInstanceDto that = (WorkflowInstanceDto) o;

    if (id != null ? !id.equals(that.id) : that.id != null)
      return false;
    if (workflowId != null ? !workflowId.equals(that.workflowId) : that.workflowId != null)
      return false;
    if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null)
      return false;
    if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null)
      return false;
    if (state != that.state)
      return false;
    if (businessKey != null ? !businessKey.equals(that.businessKey) : that.businessKey != null)
      return false;
    return incidents != null ? incidents.equals(that.incidents) : that.incidents == null;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (workflowId != null ? workflowId.hashCode() : 0);
    result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
    result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
    result = 31 * result + (state != null ? state.hashCode() : 0);
    result = 31 * result + (businessKey != null ? businessKey.hashCode() : 0);
    result = 31 * result + (incidents != null ? incidents.hashCode() : 0);
    return result;
  }
}
