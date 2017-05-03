package org.camunda.optimize.dto.optimize;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ProcessInstanceDto implements OptimizeDto {

  protected String processDefinitionKey;
  protected String processDefinitionId;
  protected String processInstanceId;
  protected Date startDate;
  protected Date endDate;
  protected List<SimpleEventDto> events = new ArrayList<>();
  protected List<SimpleVariableDto> variables = new ArrayList<>();

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public Date getStartDate() {
    return startDate;
  }

  public void setStartDate(Date startDate) {
    this.startDate = startDate;
  }

  public Date getEndDate() {
    return endDate;
  }

  public void setEndDate(Date endDate) {
    this.endDate = endDate;
  }

  public List<SimpleEventDto> getEvents() {
    return events;
  }

  public void setEvents(List<SimpleEventDto> events) {
    this.events = events;
  }

  public List<SimpleVariableDto> getVariables() {
    return variables;
  }

  public void setVariables(List<SimpleVariableDto> variables) {
    this.variables = variables;
  }
}
