/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto.incidents;

import java.util.ArrayList;
import java.util.List;

@Deprecated
public class IncidentResponseOldDto {

  private long count;

  private List<IncidentOldDto> incidents = new ArrayList<>();

  private List<IncidentErrorTypeOldDto> errorTypes = new ArrayList<>();

  private List<IncidentFlowNodeOldDto> flowNodes = new ArrayList<>();

  public long getCount() {
    return count;
  }

  public void setCount(long count) {
    this.count = count;
  }

  public List<IncidentOldDto> getIncidents() {
    return incidents;
  }

  public void setIncidents(List<IncidentOldDto> incidents) {
    this.incidents = incidents;
  }

  public List<IncidentErrorTypeOldDto> getErrorTypes() {
    return errorTypes;
  }

  public void setErrorTypes(List<IncidentErrorTypeOldDto> errorTypes) {
    this.errorTypes = errorTypes;
  }

  public List<IncidentFlowNodeOldDto> getFlowNodes() {
    return flowNodes;
  }

  public void setFlowNodes(List<IncidentFlowNodeOldDto> flowNodes) {
    this.flowNodes = flowNodes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    IncidentResponseOldDto that = (IncidentResponseOldDto) o;

    if (count != that.count)
      return false;
    if (incidents != null ? !incidents.equals(that.incidents) : that.incidents != null)
      return false;
    if (errorTypes != null ? !errorTypes.equals(that.errorTypes) : that.errorTypes != null)
      return false;
    return flowNodes != null ? flowNodes.equals(that.flowNodes) : that.flowNodes == null;
  }

  @Override
  public int hashCode() {
    int result = (int) (count ^ (count >>> 32));
    result = 31 * result + (incidents != null ? incidents.hashCode() : 0);
    result = 31 * result + (errorTypes != null ? errorTypes.hashCode() : 0);
    result = 31 * result + (flowNodes != null ? flowNodes.hashCode() : 0);
    return result;
  }
}
