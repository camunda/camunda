/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.incidents;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class IncidentsByProcessGroupStatisticsDto {

  public static final Comparator<IncidentsByProcessGroupStatisticsDto> COMPARATOR =
      new IncidentsByProcessGroupStatisticsDtoComparator();

  private String bpmnProcessId;

  private String tenantId;

  private String processName;

  private long instancesWithActiveIncidentsCount;

  private long activeInstancesCount;

  @JsonDeserialize(as = TreeSet.class) // for tests
  private Set<IncidentByProcessStatisticsDto> processes =
      new TreeSet<>(IncidentByProcessStatisticsDto.COMPARATOR);

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public IncidentsByProcessGroupStatisticsDto setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
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
  public int hashCode() {
    return Objects.hash(
        bpmnProcessId,
        tenantId,
        processName,
        instancesWithActiveIncidentsCount,
        activeInstancesCount,
        processes);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IncidentsByProcessGroupStatisticsDto that = (IncidentsByProcessGroupStatisticsDto) o;
    return instancesWithActiveIncidentsCount == that.instancesWithActiveIncidentsCount
        && activeInstancesCount == that.activeInstancesCount
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(processName, that.processName)
        && Objects.equals(processes, that.processes);
  }

  public static class IncidentsByProcessGroupStatisticsDtoComparator
      implements Comparator<IncidentsByProcessGroupStatisticsDto> {
    @Override
    public int compare(
        IncidentsByProcessGroupStatisticsDto o1, IncidentsByProcessGroupStatisticsDto o2) {
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
      int result =
          Long.compare(
              o2.getInstancesWithActiveIncidentsCount(), o1.getInstancesWithActiveIncidentsCount());
      if (result == 0) {
        result = Long.compare(o2.getActiveInstancesCount(), o1.getActiveInstancesCount());
        if (result == 0) {
          result = o1.getBpmnProcessId().compareTo(o2.getBpmnProcessId());
          if (result == 0) {
            result = o1.getTenantId().compareTo(o2.getTenantId());
          }
        }
      }
      return result;
    }
  }
}
