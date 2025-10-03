/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;

import java.util.List;
import java.util.Objects;

public class ProcessVariableReportValuesRequestDto {

  private List<String> reportIds;
  private String name;
  private VariableType type;
  private String valueFilter;
  private Integer resultOffset = 0;
  private Integer numResults = MAX_RESPONSE_SIZE_LIMIT;

  public ProcessVariableReportValuesRequestDto() {}

  public List<String> getReportIds() {
    return reportIds;
  }

  public void setReportIds(final List<String> reportIds) {
    this.reportIds = reportIds;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public VariableType getType() {
    return type;
  }

  public void setType(final VariableType type) {
    this.type = type;
  }

  public String getValueFilter() {
    return valueFilter;
  }

  public void setValueFilter(final String valueFilter) {
    this.valueFilter = valueFilter;
  }

  public Integer getResultOffset() {
    return resultOffset;
  }

  public void setResultOffset(final Integer resultOffset) {
    this.resultOffset = resultOffset;
  }

  public Integer getNumResults() {
    return numResults;
  }

  public void setNumResults(final Integer numResults) {
    this.numResults = numResults;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessVariableReportValuesRequestDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(reportIds, name, type, valueFilter, resultOffset, numResults);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessVariableReportValuesRequestDto that = (ProcessVariableReportValuesRequestDto) o;
    return Objects.equals(reportIds, that.reportIds)
        && Objects.equals(name, that.name)
        && Objects.equals(type, that.type)
        && Objects.equals(valueFilter, that.valueFilter)
        && Objects.equals(resultOffset, that.resultOffset)
        && Objects.equals(numResults, that.numResults);
  }

  @Override
  public String toString() {
    return "ProcessVariableReportValuesRequestDto(reportIds="
        + getReportIds()
        + ", name="
        + getName()
        + ", type="
        + getType()
        + ", valueFilter="
        + getValueFilter()
        + ", resultOffset="
        + getResultOffset()
        + ", numResults="
        + getNumResults()
        + ")";
  }
}
