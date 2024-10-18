/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard.filter;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = DashboardInstanceStartDateFilterDto.class, name = "instanceStartDate"),
  @JsonSubTypes.Type(value = DashboardInstanceEndDateFilterDto.class, name = "instanceEndDate"),
  @JsonSubTypes.Type(value = DashboardStateFilterDto.class, name = "state"),
  @JsonSubTypes.Type(value = DashboardVariableFilterDto.class, name = "variable"),
  @JsonSubTypes.Type(value = DashboardAssigneeFilterDto.class, name = "assignee"),
  @JsonSubTypes.Type(value = DashboardCandidateGroupFilterDto.class, name = "candidateGroup")
})
public abstract class DashboardFilterDto<DATA extends FilterDataDto> {

  protected DATA data;

  protected DashboardFilterDto(final DATA data) {
    this.data = data;
  }

  public DashboardFilterDto() {}

  public DATA getData() {
    return data;
  }

  public void setData(final DATA data) {
    this.data = data;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DashboardFilterDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $data = getData();
    result = result * PRIME + ($data == null ? 43 : $data.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DashboardFilterDto)) {
      return false;
    }
    final DashboardFilterDto<?> other = (DashboardFilterDto<?>) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$data = getData();
    final Object other$data = other.getData();
    if (this$data == null ? other$data != null : !this$data.equals(other$data)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DashboardFilterDto(data=" + getData() + ")";
  }

  public static final class Fields {

    public static final String data = "data";
  }
}
