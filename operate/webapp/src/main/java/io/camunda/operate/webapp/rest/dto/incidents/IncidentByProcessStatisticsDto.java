/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.incidents;

import java.util.Comparator;
import java.util.Objects;

public class IncidentByProcessStatisticsDto implements Comparable<IncidentByProcessStatisticsDto> {

  public static final Comparator<IncidentByProcessStatisticsDto> COMPARATOR =
      new IncidentByProcessStatisticsDtoComparator();

  private String processId;

  private int version;

  private String name;

  private String bpmnProcessId;

  private String tenantId;

  private String errorMessage;

  private long instancesWithActiveIncidentsCount;

  private long activeInstancesCount;

  public IncidentByProcessStatisticsDto() {}

  public IncidentByProcessStatisticsDto(
      String processId, long instancesWithActiveIncidentsCount, long activeInstancesCount) {
    this.processId = processId;
    this.instancesWithActiveIncidentsCount = instancesWithActiveIncidentsCount;
    this.activeInstancesCount = activeInstancesCount;
  }

  public IncidentByProcessStatisticsDto(
      String processId, String errorMessage, long instancesWithActiveIncidentsCount) {
    this.processId = processId;
    this.errorMessage = errorMessage;
    this.instancesWithActiveIncidentsCount = instancesWithActiveIncidentsCount;
  }

  public String getProcessId() {
    return processId;
  }

  public void setProcessId(String processId) {
    this.processId = processId;
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

  public String getTenantId() {
    return tenantId;
  }

  public IncidentByProcessStatisticsDto setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
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

  public long getActiveInstancesCount() {
    return activeInstancesCount;
  }

  public void setActiveInstancesCount(long activeInstancesCount) {
    this.activeInstancesCount = activeInstancesCount;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        processId,
        version,
        name,
        bpmnProcessId,
        tenantId,
        errorMessage,
        instancesWithActiveIncidentsCount,
        activeInstancesCount);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IncidentByProcessStatisticsDto that = (IncidentByProcessStatisticsDto) o;
    return version == that.version
        && instancesWithActiveIncidentsCount == that.instancesWithActiveIncidentsCount
        && activeInstancesCount == that.activeInstancesCount
        && Objects.equals(processId, that.processId)
        && Objects.equals(name, that.name)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(errorMessage, that.errorMessage);
  }

  @Override
  public int compareTo(IncidentByProcessStatisticsDto o) {
    return COMPARATOR.compare(this, o);
  }

  public static class IncidentByProcessStatisticsDtoComparator
      implements Comparator<IncidentByProcessStatisticsDto> {

    @Override
    @SuppressWarnings("checkstyle:NestedIfDepth")
    public int compare(IncidentByProcessStatisticsDto o1, IncidentByProcessStatisticsDto o2) {
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
          result =
              emptyStringWhenNull(o1.getBpmnProcessId())
                  .compareTo(emptyStringWhenNull(o2.getBpmnProcessId()));
          if (result == 0) {
            result =
                emptyStringWhenNull(o1.getTenantId())
                    .compareTo(emptyStringWhenNull(o2.getTenantId()));
            if (result == 0) {
              result = Integer.compare(o1.getVersion(), o2.getVersion());
            }
          }
        }
      }
      return result;
    }

    private String emptyStringWhenNull(String aString) {
      return aString == null ? "" : aString;
    }
  }
}
