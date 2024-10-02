/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto;

import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.webapps.schema.descriptors.operate.template.JobTemplate;
import java.util.Objects;
import java.util.Set;

public class ListenerRequestDto extends PaginatedQuery<ListenerRequestDto> {
  private String flowNodeId;

  public ListenerRequestDto() {}

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public ListenerRequestDto setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  @Override
  protected Set<String> getValidSortByValues() {
    return Set.of(JobTemplate.TIME);
  }

  @Override
  public ListenerRequestDto setSearchAfterOrEqual(final SortValuesWrapper[] searchAfterOrEqual) {
    if (searchAfterOrEqual != null) {
      throw new InvalidRequestException("SearchAfterOrEqual is not supported.");
    }
    return this;
  }

  @Override
  public ListenerRequestDto setSearchBeforeOrEqual(final SortValuesWrapper[] searchBeforeOrEqual) {
    if (searchBeforeOrEqual != null) {
      throw new InvalidRequestException("SearchBeforeOrEqual is not supported.");
    }
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), flowNodeId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final ListenerRequestDto that = (ListenerRequestDto) o;
    return Objects.equals(flowNodeId, that.flowNodeId);
  }

  @Override
  public String toString() {
    return "ListenerRequestDto{"
        + "flowNodeId='"
        + flowNodeId
        + '\''
        + ", pageSize="
        + pageSize
        + '}';
  }
}