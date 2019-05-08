/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest.dto.incidents;

import java.util.Comparator;

public class IncidentByWorkflowStatisticsDto implements Comparable<IncidentByWorkflowStatisticsDto> {


  public final static Comparator<IncidentByWorkflowStatisticsDto> COMPARATOR = new IncidentByWorkflowStatisticsDtoComparator();
  
  private String workflowId;

  private int version;

  private String name;

  private String bpmnProcessId;

  private String errorMessage;

  private long instancesWithActiveIncidentsCount;

  private Long activeInstancesCount;

  public IncidentByWorkflowStatisticsDto() {
  }

  public IncidentByWorkflowStatisticsDto(String workflowId, long instancesWithActiveIncidentsCount, long activeInstancesCount) {
    this.workflowId = workflowId;
    this.instancesWithActiveIncidentsCount = instancesWithActiveIncidentsCount;
    this.activeInstancesCount = activeInstancesCount;
  }

  public IncidentByWorkflowStatisticsDto(String workflowId, String errorMessage, long instancesWithActiveIncidentsCount) {
    this.workflowId = workflowId;
    this.errorMessage = errorMessage;
    this.instancesWithActiveIncidentsCount = instancesWithActiveIncidentsCount;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public void setWorkflowId(String workflowId) {
    this.workflowId = workflowId;
  }

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public long getInstancesWithActiveIncidentsCount() {
    return instancesWithActiveIncidentsCount;
  }

  public void setInstancesWithActiveIncidentsCount(long instancesWithActiveIncidentsCount) {
    this.instancesWithActiveIncidentsCount = instancesWithActiveIncidentsCount;
  }

  public Long getActiveInstancesCount() {
    return activeInstancesCount;
  }

  public void setActiveInstancesCount(Long activeInstancesCount) {
    this.activeInstancesCount = activeInstancesCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    IncidentByWorkflowStatisticsDto that = (IncidentByWorkflowStatisticsDto) o;

    if (version != that.version)
      return false;
    if (instancesWithActiveIncidentsCount != that.instancesWithActiveIncidentsCount)
      return false;
    if (workflowId != null ? !workflowId.equals(that.workflowId) : that.workflowId != null)
      return false;
    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;
    if (bpmnProcessId != null ? !bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId != null)
      return false;
    if (errorMessage != null ? !errorMessage.equals(that.errorMessage) : that.errorMessage != null)
      return false;
    return activeInstancesCount != null ? activeInstancesCount.equals(that.activeInstancesCount) : that.activeInstancesCount == null;
  }

  @Override
  public int hashCode() {
    int result = workflowId != null ? workflowId.hashCode() : 0;
    result = 31 * result + version;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
    result = 31 * result + (int) (instancesWithActiveIncidentsCount ^ (instancesWithActiveIncidentsCount >>> 32));
    result = 31 * result + (activeInstancesCount != null ? activeInstancesCount.hashCode() : 0);
    return result;
  }

  @Override
  public int compareTo(IncidentByWorkflowStatisticsDto o) {
    if (o == null){
      return 1;
    }
    final IncidentByWorkflowStatisticsDto stat = (IncidentByWorkflowStatisticsDto) o;
    int compare = Long.compare(stat.getInstancesWithActiveIncidentsCount(), this.getInstancesWithActiveIncidentsCount());
    if (compare == 0) {
      compare = this.getWorkflowId().compareTo(stat.getWorkflowId());
    }
    return compare;
  }
  
  public static class IncidentByWorkflowStatisticsDtoComparator implements Comparator<IncidentByWorkflowStatisticsDto>{
    
    @Override
    public int compare(IncidentByWorkflowStatisticsDto o1, IncidentByWorkflowStatisticsDto o2) {
      if (o1 == null) {
        if (o2 == null) {
          return 0;
        } else {
          return 1;
        }
      }
      if (o2 == null) {
        return -1;
      }
      if (o1.equals(o2)) {
        return 0;
      }
      int result = Long.compare(o2.getInstancesWithActiveIncidentsCount(), o1.getInstancesWithActiveIncidentsCount());
      if (result == 0) {
        result = Long.compare(o2.getActiveInstancesCount(), o1.getActiveInstancesCount());
        if(result == 0) {
          result = o1.getBpmnProcessId().compareTo(o2.getBpmnProcessId());
        } 
        if(result == 0) {
          result = Integer.compare(o1.getVersion(),o2.getVersion());
        }
      }
      return result;
    }
  }
}
