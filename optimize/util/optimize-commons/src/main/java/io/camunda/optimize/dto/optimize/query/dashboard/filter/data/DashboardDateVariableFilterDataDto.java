/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard.filter.data;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.DashboardVariableFilterSubDataDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;

public class DashboardDateVariableFilterDataDto extends DashboardVariableFilterDataDto {

  protected DateFilterDataDto<?> defaultValues;

  protected DashboardDateVariableFilterDataDto() {
    this(null);
  }

  public DashboardDateVariableFilterDataDto(final String name) {
    this(name, null, null);
  }

  public DashboardDateVariableFilterDataDto(
      final String name,
      final DashboardVariableFilterSubDataDto data,
      final DateFilterDataDto<?> defaultValues) {
    super(VariableType.DATE, name, data);
    this.defaultValues = defaultValues;
  }

  public DateFilterDataDto<?> getDefaultValues() {
    return defaultValues;
  }

  public void setDefaultValues(final DateFilterDataDto<?> defaultValues) {
    this.defaultValues = defaultValues;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof DashboardDateVariableFilterDataDto;
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
    if (!(o instanceof DashboardDateVariableFilterDataDto)) {
      return false;
    }
    final DashboardDateVariableFilterDataDto other = (DashboardDateVariableFilterDataDto) o;
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
    return "DashboardDateVariableFilterDataDto(defaultValues=" + getDefaultValues() + ")";
  }
}
