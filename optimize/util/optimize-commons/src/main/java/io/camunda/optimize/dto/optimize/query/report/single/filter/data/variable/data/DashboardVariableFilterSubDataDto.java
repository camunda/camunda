/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.OperatorMultipleValuesFilterDataDto;
import java.util.List;

public class DashboardVariableFilterSubDataDto extends OperatorMultipleValuesFilterDataDto {

  protected boolean allowCustomValues;

  public DashboardVariableFilterSubDataDto(
      final FilterOperator operator, final List<String> values, final boolean allowCustomValues) {
    super(operator, values);
    this.allowCustomValues = allowCustomValues;
  }

  protected DashboardVariableFilterSubDataDto() {}

  public boolean isAllowCustomValues() {
    return allowCustomValues;
  }

  public void setAllowCustomValues(final boolean allowCustomValues) {
    this.allowCustomValues = allowCustomValues;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof DashboardVariableFilterSubDataDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    result = result * PRIME + (isAllowCustomValues() ? 79 : 97);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DashboardVariableFilterSubDataDto)) {
      return false;
    }
    final DashboardVariableFilterSubDataDto other = (DashboardVariableFilterSubDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    if (isAllowCustomValues() != other.isAllowCustomValues()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DashboardVariableFilterSubDataDto(super="
        + super.toString()
        + ", allowCustomValues="
        + isAllowCustomValues()
        + ")";
  }

  public static final class Fields {

    public static final String allowCustomValues = "allowCustomValues";
  }
}
