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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $operator = getOperator();
    result = result * PRIME + ($operator == null ? 43 : $operator.hashCode());
    final Object $values = getValues();
    result = result * PRIME + ($values == null ? 43 : $values.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof IdentityLinkFilterDataDto)) {
      return false;
    }
    final IdentityLinkFilterDataDto other = (IdentityLinkFilterDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$operator = getOperator();
    final Object other$operator = other.getOperator();
    if (this$operator == null ? other$operator != null : !this$operator.equals(other$operator)) {
      return false;
    }
    final Object this$values = getValues();
    final Object other$values = other.getValues();
    if (this$values == null ? other$values != null : !this$values.equals(other$values)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "IdentityLinkFilterDataDto(operator=" + getOperator() + ", values=" + getValues() + ")";
  }
}
