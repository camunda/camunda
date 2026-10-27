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

public class DashboardBooleanVariableFilterDataDto extends DashboardVariableFilterDataDto {

  protected List<Boolean> defaultValues;

  protected DashboardBooleanVariableFilterDataDto() {
    this(null);
  }

  public DashboardBooleanVariableFilterDataDto(final String name) {
    this(name, null, null);
  }

  public DashboardBooleanVariableFilterDataDto(
      final String name,
      final DashboardVariableFilterSubDataDto data,
      final List<Boolean> defaultValues) {
    super(VariableType.BOOLEAN, name, data);
    this.defaultValues = defaultValues;
  }

  public List<Boolean> getDefaultValues() {
    return defaultValues;
  }

  public void setDefaultValues(final List<Boolean> defaultValues) {
    this.defaultValues = defaultValues;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof DashboardBooleanVariableFilterDataDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final DashboardBooleanVariableFilterDataDto that = (DashboardBooleanVariableFilterDataDto) o;
    return Objects.equals(defaultValues, that.defaultValues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), defaultValues);
  }

  @Override
  public String toString() {
    return "DashboardBooleanVariableFilterDataDto(defaultValues=" + getDefaultValues() + ")";
  }
}
