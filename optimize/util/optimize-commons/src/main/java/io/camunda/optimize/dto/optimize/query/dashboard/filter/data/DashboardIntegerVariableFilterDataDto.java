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
import java.util.Objects;

public class DashboardIntegerVariableFilterDataDto extends DashboardVariableFilterDataDto {

  protected List<String> defaultValues;

  protected DashboardIntegerVariableFilterDataDto() {
    this(null, new DashboardVariableFilterSubDataDto(null, null, false));
  }

  public DashboardIntegerVariableFilterDataDto(
      final String name, final DashboardVariableFilterSubDataDto data) {
    this(name, data, null);
  }

  public DashboardIntegerVariableFilterDataDto(
      final String name,
      final DashboardVariableFilterSubDataDto data,
      final List<String> defaultValues) {
    super(VariableType.INTEGER, name, data);
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
    return other instanceof DashboardIntegerVariableFilterDataDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final DashboardIntegerVariableFilterDataDto that = (DashboardIntegerVariableFilterDataDto) o;
    return Objects.equals(defaultValues, that.defaultValues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), defaultValues);
  }

  @Override
  public String toString() {
    return "DashboardIntegerVariableFilterDataDto(defaultValues=" + getDefaultValues() + ")";
  }
}
