/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.incidents;

import java.util.Comparator;

public class IncidentByProcessStatisticsDto implements Comparable<IncidentByProcessStatisticsDto> {


  public final static Comparator<IncidentByProcessStatisticsDto> COMPARATOR = new IncidentByProcessStatisticsDtoComparator();
  
  private String processId;

  private int version;

  private String name;

  private String bpmnProcessId;

  private String errorMessage;

  private long instancesWithActiveIncidentsCount;

  private long activeInstancesCount;

  public IncidentByProcessStatisticsDto() {
  }

  public IncidentByProcessStatisticsDto(String processId, long instancesWithActiveIncidentsCount, long activeInstancesCount) {
    this.processId = processId;
    this.instancesWithActiveIncidentsCount = instancesWithActiveIncidentsCount;
    this.activeInstancesCount = activeInstancesCount;
  }

  public IncidentByProcessStatisticsDto(String processId, String errorMessage, long instancesWithActiveIncidentsCount) {
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
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    IncidentByProcessStatisticsDto that = (IncidentByProcessStatisticsDto) o;

    if (version != that.version)
      return false;
    if (instancesWithActiveIncidentsCount != that.instancesWithActiveIncidentsCount)
      return false;
    if (processId != null ? !processId.equals(that.processId) : that.processId != null)
      return false;
    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;
    if (bpmnProcessId != null ? !bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId != null)
      return false;
    if (errorMessage != null ? !errorMessage.equals(that.errorMessage) : that.errorMessage != null)
      return false;
    return activeInstancesCount == that.activeInstancesCount;
  }

  @Override
  public int hashCode() {
    int result = processId != null ? processId.hashCode() : 0;
    result = 31 * result + version;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
    result = 31 * result + (int) (instancesWithActiveIncidentsCount ^ (instancesWithActiveIncidentsCount >>> 32));
    result = 31 * result + (int) activeInstancesCount;
    return result;
  }

  @Override
  public int compareTo(IncidentByProcessStatisticsDto o) {
    return COMPARATOR.compare(this, o);
  }
  
  public static class IncidentByProcessStatisticsDtoComparator implements Comparator<IncidentByProcessStatisticsDto>{
    
    @Override
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
      int result = Long.compare(o2.getInstancesWithActiveIncidentsCount(), o1.getInstancesWithActiveIncidentsCount());
      if (result == 0) {
        result = Long.compare(o2.getActiveInstancesCount(), o1.getActiveInstancesCount());
        if (result == 0) {
          result = emptyStringWhenNull(o1.getBpmnProcessId()).compareTo(emptyStringWhenNull(o2.getBpmnProcessId()));
          if (result == 0) {
            result = Integer.compare(o1.getVersion(), o2.getVersion());
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
