/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter.data;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import java.util.List;
import java.util.Objects;

public class IdentityLinkFilterDataDto implements FilterDataDto {

  protected MembershipFilterOperator operator;
  protected List<String> values;

  public IdentityLinkFilterDataDto(
      final MembershipFilterOperator operator, final List<String> values) {
    this.operator = operator;
    this.values = values;
  }

  protected IdentityLinkFilterDataDto() {}

  public MembershipFilterOperator getOperator() {
    return operator;
  }

  public void setOperator(final MembershipFilterOperator operator) {
    this.operator = operator;
  }

  public List<String> getValues() {
    return values;
  }

  public void setValues(final List<String> values) {
    this.values = values;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof IdentityLinkFilterDataDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IdentityLinkFilterDataDto that = (IdentityLinkFilterDataDto) o;
    return operator == that.operator && Objects.equals(values, that.values);
  }

  @Override
  public int hashCode() {
    return Objects.hash(operator, values);
  }

  @Override
  public String toString() {
    return "IdentityLinkFilterDataDto(operator=" + getOperator() + ", values=" + getValues() + ")";
  }
}
