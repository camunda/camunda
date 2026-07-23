/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import java.util.List;
import java.util.Objects;

/**
 * Constrained request body for evaluating an agentic control dashboard report tile through the
 * public API. It deliberately exposes only the two filters that the agentic control dashboard
 * itself uses:
 *
 * <ul>
 *   <li>a process scope filter ({@link #processDefinitionKeys}) and
 *   <li>a rolling instance end date filter ({@link #endDateRollingValue} + {@link
 *       #endDateRollingUnit}).
 * </ul>
 *
 * Any other filter type is intentionally not accepted, so this endpoint cannot be used as a general
 * purpose filtered-evaluation API.
 */
public class PublicAgenticDashboardEvaluationRequestDto {

  /** Process definition keys to scope the evaluation to. {@code null} or empty means all. */
  private List<String> processDefinitionKeys;

  /** Rolling instance end date window size, e.g. {@code 30}. {@code null} means no date filter. */
  private Long endDateRollingValue;

  /** Rolling instance end date window unit, e.g. {@code DAYS}. Required if a value is given. */
  private DateUnit endDateRollingUnit;

  public PublicAgenticDashboardEvaluationRequestDto() {}

  public List<String> getProcessDefinitionKeys() {
    return processDefinitionKeys;
  }

  public void setProcessDefinitionKeys(final List<String> processDefinitionKeys) {
    this.processDefinitionKeys = processDefinitionKeys;
  }

  public Long getEndDateRollingValue() {
    return endDateRollingValue;
  }

  public void setEndDateRollingValue(final Long endDateRollingValue) {
    this.endDateRollingValue = endDateRollingValue;
  }

  public DateUnit getEndDateRollingUnit() {
    return endDateRollingUnit;
  }

  public void setEndDateRollingUnit(final DateUnit endDateRollingUnit) {
    this.endDateRollingUnit = endDateRollingUnit;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PublicAgenticDashboardEvaluationRequestDto that =
        (PublicAgenticDashboardEvaluationRequestDto) o;
    return Objects.equals(processDefinitionKeys, that.processDefinitionKeys)
        && Objects.equals(endDateRollingValue, that.endDateRollingValue)
        && endDateRollingUnit == that.endDateRollingUnit;
  }

  @Override
  public int hashCode() {
    return Objects.hash(processDefinitionKeys, endDateRollingValue, endDateRollingUnit);
  }

  @Override
  public String toString() {
    return "PublicAgenticDashboardEvaluationRequestDto(processDefinitionKeys="
        + processDefinitionKeys
        + ", endDateRollingValue="
        + endDateRollingValue
        + ", endDateRollingUnit="
        + endDateRollingUnit
        + ")";
  }
}
