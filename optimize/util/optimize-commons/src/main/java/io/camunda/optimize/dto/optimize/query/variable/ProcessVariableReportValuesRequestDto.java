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
    final int PRIME = 59;
    int result = 1;
    final Object $reportIds = getReportIds();
    result = result * PRIME + ($reportIds == null ? 43 : $reportIds.hashCode());
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    final Object $valueFilter = getValueFilter();
    result = result * PRIME + ($valueFilter == null ? 43 : $valueFilter.hashCode());
    final Object $resultOffset = getResultOffset();
    result = result * PRIME + ($resultOffset == null ? 43 : $resultOffset.hashCode());
    final Object $numResults = getNumResults();
    result = result * PRIME + ($numResults == null ? 43 : $numResults.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ProcessVariableReportValuesRequestDto)) {
      return false;
    }
    final ProcessVariableReportValuesRequestDto other = (ProcessVariableReportValuesRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$reportIds = getReportIds();
    final Object other$reportIds = other.getReportIds();
    if (this$reportIds == null
        ? other$reportIds != null
        : !this$reportIds.equals(other$reportIds)) {
      return false;
    }
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    final Object this$valueFilter = getValueFilter();
    final Object other$valueFilter = other.getValueFilter();
    if (this$valueFilter == null
        ? other$valueFilter != null
        : !this$valueFilter.equals(other$valueFilter)) {
      return false;
    }
    final Object this$resultOffset = getResultOffset();
    final Object other$resultOffset = other.getResultOffset();
    if (this$resultOffset == null
        ? other$resultOffset != null
        : !this$resultOffset.equals(other$resultOffset)) {
      return false;
    }
    final Object this$numResults = getNumResults();
    final Object other$numResults = other.getNumResults();
    if (this$numResults == null
        ? other$numResults != null
        : !this$numResults.equals(other$numResults)) {
      return false;
    }
    return true;
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
