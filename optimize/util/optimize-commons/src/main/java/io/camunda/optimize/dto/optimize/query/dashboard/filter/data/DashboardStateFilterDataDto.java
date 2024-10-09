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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $defaultValues = getDefaultValues();
    result = result * PRIME + ($defaultValues == null ? 43 : $defaultValues.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DashboardStateFilterDataDto)) {
      return false;
    }
    final DashboardStateFilterDataDto other = (DashboardStateFilterDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$defaultValues = getDefaultValues();
    final Object other$defaultValues = other.getDefaultValues();
    if (this$defaultValues == null
        ? other$defaultValues != null
        : !this$defaultValues.equals(other$defaultValues)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DashboardStateFilterDataDto(defaultValues=" + getDefaultValues() + ")";
  }

  public static final class Fields {

    public static final String defaultValues = "defaultValues";
  }
}
