/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard.filter.data;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.DashboardVariableFilterSubDataDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import java.util.List;

public class DashboardDoubleVariableFilterDataDto extends DashboardVariableFilterDataDto {

  protected List<String> defaultValues;

  protected DashboardDoubleVariableFilterDataDto() {
    this(null, new DashboardVariableFilterSubDataDto(null, null, false));
  }

  public DashboardDoubleVariableFilterDataDto(
      final String name, final DashboardVariableFilterSubDataDto data) {
    this(name, data, null);
  }

  public DashboardDoubleVariableFilterDataDto(
      final String name,
      final DashboardVariableFilterSubDataDto data,
      final List<String> defaultValues) {
    super(VariableType.DOUBLE, name, data);
    this.defaultValues = defaultValues;
  }

  public List<String> getDefaultValues() {
    return defaultValues;
  }

  public void setDefaultValues(final List<String> defaultValues) {
    this.defaultValues = defaultValues;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof DashboardDoubleVariableFilterDataDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $defaultValues = getDefaultValues();
    result = result * PRIME + ($defaultValues == null ? 43 : $defaultValues.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DashboardDoubleVariableFilterDataDto)) {
      return false;
    }
    final DashboardDoubleVariableFilterDataDto other = (DashboardDoubleVariableFilterDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
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
    return "DashboardDoubleVariableFilterDataDto(defaultValues=" + getDefaultValues() + ")";
  }
}
