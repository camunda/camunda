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

public class IncidentsByErrorMsgStatisticsDto {

  public static final Comparator<IncidentsByErrorMsgStatisticsDto> COMPARATOR =
      new IncidentsByErrorMsgStatisticsDtoComparator();

  private String errorMessage;

  private Integer incidentErrorHashCode;

  private long instancesWithErrorCount;

  @JsonDeserialize(as = TreeSet.class) // for tests
  private Set<IncidentByProcessStatisticsDto> processes = new TreeSet<>();

  public IncidentsByErrorMsgStatisticsDto() {}

  public IncidentsByErrorMsgStatisticsDto(String errorMessage, Integer incidentErrorHashCode) {
    this.errorMessage = errorMessage;
    this.incidentErrorHashCode = incidentErrorHashCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Integer getIncidentErrorHashCode() {
    return incidentErrorHashCode;
  }

  public long getInstancesWithErrorCount() {
    return instancesWithErrorCount;
  }

  public void setInstancesWithErrorCount(long instancesWithErrorCount) {
    this.instancesWithErrorCount = instancesWithErrorCount;
  }

  public Set<IncidentByProcessStatisticsDto> getProcesses() {
    return processes;
  }

  public void setProcesses(Set<IncidentByProcessStatisticsDto> processes) {
    this.processes = processes;
  }

  public void recordInstancesCount(long count) {
    this.instancesWithErrorCount += count;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IncidentsByErrorMsgStatisticsDto that = (IncidentsByErrorMsgStatisticsDto) o;
    return instancesWithErrorCount == that.instancesWithErrorCount
        && Objects.equals(errorMessage, that.errorMessage)
        && Objects.equals(incidentErrorHashCode, that.incidentErrorHashCode)
        && Objects.equals(processes, that.processes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(errorMessage, incidentErrorHashCode, instancesWithErrorCount, processes);
  }

  public static class IncidentsByErrorMsgStatisticsDtoComparator
      implements Comparator<IncidentsByErrorMsgStatisticsDto> {

    @Override
    public int compare(IncidentsByErrorMsgStatisticsDto o1, IncidentsByErrorMsgStatisticsDto o2) {
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
      int result = Long.compare(o2.getInstancesWithErrorCount(), o1.getInstancesWithErrorCount());
      if (result == 0) {
        result = o1.getErrorMessage().compareTo(o2.getErrorMessage());
      }
      return result;
    }
  }
}
