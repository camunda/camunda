/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard.filter.data;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import java.util.List;
import java.util.Objects;

public class DashboardStateFilterDataDto implements FilterDataDto {

  private List<String> defaultValues;

  public DashboardStateFilterDataDto(final List<String> defaultValues) {
    this.defaultValues = defaultValues;
  }

  protected DashboardStateFilterDataDto() {}

  public List<String> getDefaultValues() {
    return defaultValues;
  }

  public void setDefaultValues(final List<String> defaultValues) {
    this.defaultValues = defaultValues;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DashboardStateFilterDataDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DashboardStateFilterDataDto that = (DashboardStateFilterDataDto) o;
    return Objects.equals(defaultValues, that.defaultValues);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(defaultValues);
  }

  @Override
  public String toString() {
    return "DashboardStateFilterDataDto(defaultValues=" + getDefaultValues() + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String defaultValues = "defaultValues";
  }
}
