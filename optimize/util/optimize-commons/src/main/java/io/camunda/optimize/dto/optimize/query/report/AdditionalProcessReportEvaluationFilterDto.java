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
import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AdditionalProcessReportEvaluationFilterDto that =
        (AdditionalProcessReportEvaluationFilterDto) o;
    return Objects.equals(filter, that.filter);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(filter);
  }

  @Override
  public String toString() {
    return "AdditionalProcessReportEvaluationFilterDto(filter=" + getFilter() + ")";
  }
}
