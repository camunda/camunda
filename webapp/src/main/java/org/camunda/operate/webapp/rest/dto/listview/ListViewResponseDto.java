/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto.listview;

import java.util.ArrayList;
import java.util.List;

public class ListViewResponseDto {

  private List<ListViewWorkflowInstanceDto> workflowInstances = new ArrayList<>();

  private long totalCount;

  public List<ListViewWorkflowInstanceDto> getWorkflowInstances() {
    return workflowInstances;
  }

  public void setWorkflowInstances(List<ListViewWorkflowInstanceDto> workflowInstances) {
    this.workflowInstances = workflowInstances;
  }

  public long getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(long totalCount) {
    this.totalCount = totalCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    ListViewResponseDto that = (ListViewResponseDto) o;

    if (totalCount != that.totalCount)
      return false;
    return workflowInstances != null ? workflowInstances.equals(that.workflowInstances) : that.workflowInstances == null;
  }

  @Override
  public int hashCode() {
    int result = workflowInstances != null ? workflowInstances.hashCode() : 0;
    result = 31 * result + (int) (totalCount ^ (totalCount >>> 32));
    return result;
  }
}
