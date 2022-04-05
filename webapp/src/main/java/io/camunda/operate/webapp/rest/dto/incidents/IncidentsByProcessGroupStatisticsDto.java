/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.incidents;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public class IncidentsByProcessGroupStatisticsDto {
  
  public static final Comparator<IncidentsByProcessGroupStatisticsDto> COMPARATOR = new IncidentsByProcessGroupStatisticsDtoComparator();

  private String bpmnProcessId;

  private String processName;

  private long instancesWithActiveIncidentsCount;

  private long activeInstancesCount;

  @JsonDeserialize(as = TreeSet.class)    //for tests
  private Set<IncidentByProcessStatisticsDto> processes = new TreeSet<>(IncidentByProcessStatisticsDto.COMPARATOR);

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public String getProcessName() {
    return processName;
  }

  public void setProcessName(String processName) {
    this.processName = processName;
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

  public Set<IncidentByProcessStatisticsDto> getProcesses() {
    return processes;
  }

  public void setProcesses(Set<IncidentByProcessStatisticsDto> processes) {
    this.processes = processes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    IncidentsByProcessGroupStatisticsDto that = (IncidentsByProcessGroupStatisticsDto) o;

    if (instancesWithActiveIncidentsCount != that.instancesWithActiveIncidentsCount)
      return false;
    if (activeInstancesCount != that.activeInstancesCount)
      return false;
    if (bpmnProcessId != null ? !bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId != null)
      return false;
    if (processName != null ? !processName.equals(that.processName) : that.processName != null)
      return false;
    return processes != null ? processes.equals(that.processes) : that.processes == null;
  }

  @Override
  public int hashCode() {
    int result = bpmnProcessId != null ? bpmnProcessId.hashCode() : 0;
    result = 31 * result + (processName != null ? processName.hashCode() : 0);
    result = 31 * result + (int) (instancesWithActiveIncidentsCount ^ (instancesWithActiveIncidentsCount >>> 32));
    result = 31 * result + (int) (activeInstancesCount ^ (activeInstancesCount >>> 32));
    result = 31 * result + (processes != null ? processes.hashCode() : 0);
    return result;
  }
  
  public static class IncidentsByProcessGroupStatisticsDtoComparator implements Comparator<IncidentsByProcessGroupStatisticsDto> { 
    @Override
    public int compare(IncidentsByProcessGroupStatisticsDto o1, IncidentsByProcessGroupStatisticsDto o2) {
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
        if (result == 0) {
          result = o1.getBpmnProcessId().compareTo(o2.getBpmnProcessId());
        }
      }
      return result;
    }
  }
}
