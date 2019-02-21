/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest.dto;

import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.rest.dto.listview.ListViewQueryDto;

public class WorkflowInstanceBatchOperationDto {

  public WorkflowInstanceBatchOperationDto() {
  }

  public WorkflowInstanceBatchOperationDto(List<ListViewQueryDto> queries) {
    this.queries = queries;
  }

  private List<ListViewQueryDto> queries = new ArrayList<>();

  private OperationType operationType;

  public List<ListViewQueryDto> getQueries() {
    return queries;
  }

  public void setQueries(List<ListViewQueryDto> queries) {
    this.queries = queries;
  }

  public OperationType getOperationType() {
    return operationType;
  }

  public void setOperationType(OperationType operationType) {
    this.operationType = operationType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    WorkflowInstanceBatchOperationDto that = (WorkflowInstanceBatchOperationDto) o;

    if (queries != null ? !queries.equals(that.queries) : that.queries != null)
      return false;
    return operationType == that.operationType;
  }

  @Override
  public int hashCode() {
    int result = queries != null ? queries.hashCode() : 0;
    result = 31 * result + (operationType != null ? operationType.hashCode() : 0);
    return result;
  }
}
