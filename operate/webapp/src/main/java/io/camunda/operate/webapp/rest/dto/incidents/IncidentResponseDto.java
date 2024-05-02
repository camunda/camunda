/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.incidents;

import java.util.ArrayList;
import java.util.List;

public class IncidentResponseDto {

  private long count;

  private List<IncidentDto> incidents = new ArrayList<>();

  private List<IncidentErrorTypeDto> errorTypes = new ArrayList<>();

  private List<IncidentFlowNodeDto> flowNodes = new ArrayList<>();

  public long getCount() {
    return count;
  }

  public void setCount(long count) {
    this.count = count;
  }

  public List<IncidentDto> getIncidents() {
    return incidents;
  }

  public void setIncidents(List<IncidentDto> incidents) {
    this.incidents = incidents;
  }

  public List<IncidentErrorTypeDto> getErrorTypes() {
    return errorTypes;
  }

  public void setErrorTypes(List<IncidentErrorTypeDto> errorTypes) {
    this.errorTypes = errorTypes;
  }

  public List<IncidentFlowNodeDto> getFlowNodes() {
    return flowNodes;
  }

  public void setFlowNodes(List<IncidentFlowNodeDto> flowNodes) {
    this.flowNodes = flowNodes;
  }

  @Override
  public int hashCode() {
    int result = (int) (count ^ (count >>> 32));
    result = 31 * result + (incidents != null ? incidents.hashCode() : 0);
    result = 31 * result + (errorTypes != null ? errorTypes.hashCode() : 0);
    result = 31 * result + (flowNodes != null ? flowNodes.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final IncidentResponseDto that = (IncidentResponseDto) o;

    if (count != that.count) {
      return false;
    }
    if (incidents != null ? !incidents.equals(that.incidents) : that.incidents != null) {
      return false;
    }
    if (errorTypes != null ? !errorTypes.equals(that.errorTypes) : that.errorTypes != null) {
      return false;
    }
    return flowNodes != null ? flowNodes.equals(that.flowNodes) : that.flowNodes == null;
  }
}
