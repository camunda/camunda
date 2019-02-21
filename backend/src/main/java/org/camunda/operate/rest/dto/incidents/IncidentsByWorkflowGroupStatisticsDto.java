/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest.dto.incidents;

import java.util.Set;
import java.util.TreeSet;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public class IncidentsByWorkflowGroupStatisticsDto {

  private String bpmnProcessId;

  private String workflowName;

  private long instancesWithActiveIncidentsCount;

  private long activeInstancesCount;

  @JsonDeserialize(as = TreeSet.class)    //for tests
  private Set<IncidentByWorkflowStatisticsDto> workflows = new TreeSet<>();

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public String getWorkflowName() {
    return workflowName;
  }

  public void setWorkflowName(String workflowName) {
    this.workflowName = workflowName;
  }

  public long getInstancesWithActiveIncidentsCount() {
    return instancesWithActiveIncidentsCount;
  }

  public void setInstancesWithActiveIncidentsCount(long instancesWithActiveIncidentsCount) {
    this.instancesWithActiveIncidentsCount = instancesWithActiveIncidentsCount;
  }

  public long getActiveInstancesCount() {
    return activeInstancesCount;
  }

  public void setActiveInstancesCount(long activeInstancesCount) {
    this.activeInstancesCount = activeInstancesCount;
  }

  public Set<IncidentByWorkflowStatisticsDto> getWorkflows() {
    return workflows;
  }

  public void setWorkflows(Set<IncidentByWorkflowStatisticsDto> workflows) {
    this.workflows = workflows;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    IncidentsByWorkflowGroupStatisticsDto that = (IncidentsByWorkflowGroupStatisticsDto) o;

    if (instancesWithActiveIncidentsCount != that.instancesWithActiveIncidentsCount)
      return false;
    if (activeInstancesCount != that.activeInstancesCount)
      return false;
    if (bpmnProcessId != null ? !bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId != null)
      return false;
    if (workflowName != null ? !workflowName.equals(that.workflowName) : that.workflowName != null)
      return false;
    return workflows != null ? workflows.equals(that.workflows) : that.workflows == null;
  }

  @Override
  public int hashCode() {
    int result = bpmnProcessId != null ? bpmnProcessId.hashCode() : 0;
    result = 31 * result + (workflowName != null ? workflowName.hashCode() : 0);
    result = 31 * result + (int) (instancesWithActiveIncidentsCount ^ (instancesWithActiveIncidentsCount >>> 32));
    result = 31 * result + (int) (activeInstancesCount ^ (activeInstancesCount >>> 32));
    result = 31 * result + (workflows != null ? workflows.hashCode() : 0);
    return result;
  }
}
