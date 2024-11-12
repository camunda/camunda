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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "DashboardFilterDto(data=" + getData() + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String data = "data";
  }
}
