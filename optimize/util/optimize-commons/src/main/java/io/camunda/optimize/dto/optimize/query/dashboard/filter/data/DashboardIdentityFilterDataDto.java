/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard.filter.data;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.IdentityLinkFilterDataDto;
import java.util.List;

public class DashboardIdentityFilterDataDto extends IdentityLinkFilterDataDto {

  protected boolean allowCustomValues;
  protected List<String> defaultValues;

  public DashboardIdentityFilterDataDto(
      final MembershipFilterOperator operator,
      final List<String> values,
      final boolean allowCustomValues) {
    super(operator, values);
    this.allowCustomValues = allowCustomValues;
  }

  public DashboardIdentityFilterDataDto(
      final MembershipFilterOperator operator,
      final List<String> values,
      final boolean allowCustomValues,
      final List<String> defaultValues) {
    super(operator, values);
    this.allowCustomValues = allowCustomValues;
    this.defaultValues = defaultValues;
  }

  protected DashboardIdentityFilterDataDto() {}

  public boolean isAllowCustomValues() {
    return allowCustomValues;
  }

  public void setAllowCustomValues(final boolean allowCustomValues) {
    this.allowCustomValues = allowCustomValues;
  }

  public List<String> getDefaultValues() {
    return defaultValues;
  }

  public void setDefaultValues(final List<String> defaultValues) {
    this.defaultValues = defaultValues;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof DashboardIdentityFilterDataDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    result = result * PRIME + (isAllowCustomValues() ? 79 : 97);
    final Object $defaultValues = getDefaultValues();
    result = result * PRIME + ($defaultValues == null ? 43 : $defaultValues.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DashboardIdentityFilterDataDto)) {
      return false;
    }
    final DashboardIdentityFilterDataDto other = (DashboardIdentityFilterDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    if (isAllowCustomValues() != other.isAllowCustomValues()) {
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
    return "DashboardIdentityFilterDataDto(allowCustomValues="
        + isAllowCustomValues()
        + ", defaultValues="
        + getDefaultValues()
        + ")";
  }

  public static final class Fields {

    public static final String allowCustomValues = "allowCustomValues";
    public static final String defaultValues = "defaultValues";
  }
}
