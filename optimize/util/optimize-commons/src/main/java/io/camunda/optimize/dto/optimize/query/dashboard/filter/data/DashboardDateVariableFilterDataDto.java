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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "DashboardDateVariableFilterDataDto(defaultValues=" + getDefaultValues() + ")";
  }
}
