/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard.filter.data;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import java.util.Objects;

public class DashboardDateFilterDataDto implements FilterDataDto {

  private DateFilterDataDto<?> defaultValues;

  public DashboardDateFilterDataDto(final DateFilterDataDto<?> defaultValues) {
    this.defaultValues = defaultValues;
  }

  protected DashboardDateFilterDataDto() {}

  public DateFilterDataDto<?> getDefaultValues() {
    return defaultValues;
  }

  public void setDefaultValues(final DateFilterDataDto<?> defaultValues) {
    this.defaultValues = defaultValues;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DashboardDateFilterDataDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DashboardDateFilterDataDto that = (DashboardDateFilterDataDto) o;
    return Objects.equals(defaultValues, that.defaultValues);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(defaultValues);
  }

  @Override
  public String toString() {
    return "DashboardDateFilterDataDto(defaultValues=" + getDefaultValues() + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String defaultValues = "defaultValues";
  }
}
