/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto.incidents;

public class IncidentFlowNodeDto {

  private String flowNodeId;

  private int count;

  public IncidentFlowNodeDto() {
  }

  public IncidentFlowNodeDto(String flowNodeId, int count) {
    this.flowNodeId = flowNodeId;
    this.count = count;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public void setFlowNodeId(String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }

  public int getCount() {
    return count;
  }

  public void setCount(int count) {
    this.count = count;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    IncidentFlowNodeDto that = (IncidentFlowNodeDto) o;

    if (count != that.count)
      return false;
    return flowNodeId != null ? flowNodeId.equals(that.flowNodeId) : that.flowNodeId == null;
  }

  @Override
  public int hashCode() {
    int result = flowNodeId != null ? flowNodeId.hashCode() : 0;
    result = 31 * result + count;
    return result;
  }
}
