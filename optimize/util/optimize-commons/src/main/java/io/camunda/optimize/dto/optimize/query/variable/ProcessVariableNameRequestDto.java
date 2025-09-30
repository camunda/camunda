/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ProcessVariableNameRequestDto {

  private List<ProcessToQueryDto> processesToQuery = new ArrayList<>();
  private List<ProcessFilterDto<?>> filter = new ArrayList<>();

  @JsonIgnore private ZoneId timezone = ZoneId.systemDefault();

  public ProcessVariableNameRequestDto(
      final List<ProcessToQueryDto> processesToQuery, final List<ProcessFilterDto<?>> filter) {
    this.processesToQuery = processesToQuery;
    this.filter = filter;
    timezone = ZoneId.systemDefault();
  }

  public ProcessVariableNameRequestDto(final List<ProcessToQueryDto> processesToQuery) {
    this(processesToQuery, Collections.emptyList());
  }

  public ProcessVariableNameRequestDto() {}

  public List<ProcessToQueryDto> getProcessesToQuery() {
    return processesToQuery;
  }

  public void setProcessesToQuery(final List<ProcessToQueryDto> processesToQuery) {
    this.processesToQuery = processesToQuery;
  }

  public List<ProcessFilterDto<?>> getFilter() {
    return filter;
  }

  public void setFilter(final List<ProcessFilterDto<?>> filter) {
    this.filter = filter;
  }

  public ZoneId getTimezone() {
    return timezone;
  }

  @JsonIgnore
  public void setTimezone(final ZoneId timezone) {
    this.timezone = timezone;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessVariableNameRequestDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(processesToQuery, filter, timezone);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessVariableNameRequestDto that = (ProcessVariableNameRequestDto) o;
    return Objects.equals(processesToQuery, that.processesToQuery)
        && Objects.equals(filter, that.filter)
        && Objects.equals(timezone, that.timezone);
  }

  @Override
  public String toString() {
    return "ProcessVariableNameRequestDto(processesToQuery="
        + getProcessesToQuery()
        + ", filter="
        + getFilter()
        + ", timezone="
        + getTimezone()
        + ")";
  }
}
