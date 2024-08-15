/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import java.util.ArrayList;
import java.util.List;

public class AdditionalProcessReportEvaluationFilterDto {

  protected List<ProcessFilterDto<?>> filter = new ArrayList<>();

  public AdditionalProcessReportEvaluationFilterDto(final List<ProcessFilterDto<?>> filter) {
    this.filter = filter;
  }

  public AdditionalProcessReportEvaluationFilterDto() {}

  public List<ProcessFilterDto<?>> getFilter() {
    return filter;
  }

  public void setFilter(final List<ProcessFilterDto<?>> filter) {
    this.filter = filter;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof AdditionalProcessReportEvaluationFilterDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $filter = getFilter();
    result = result * PRIME + ($filter == null ? 43 : $filter.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AdditionalProcessReportEvaluationFilterDto)) {
      return false;
    }
    final AdditionalProcessReportEvaluationFilterDto other =
        (AdditionalProcessReportEvaluationFilterDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$filter = getFilter();
    final Object other$filter = other.getFilter();
    if (this$filter == null ? other$filter != null : !this$filter.equals(other$filter)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "AdditionalProcessReportEvaluationFilterDto(filter=" + getFilter() + ")";
  }
}
