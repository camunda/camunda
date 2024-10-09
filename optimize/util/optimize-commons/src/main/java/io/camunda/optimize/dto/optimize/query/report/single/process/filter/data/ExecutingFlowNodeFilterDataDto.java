/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter.data;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import java.util.List;

public class ExecutingFlowNodeFilterDataDto implements FilterDataDto {

  protected List<String> values;

  public ExecutingFlowNodeFilterDataDto() {}

  public List<String> getValues() {
    return values;
  }

  public void setValues(final List<String> values) {
    this.values = values;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ExecutingFlowNodeFilterDataDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $values = getValues();
    result = result * PRIME + ($values == null ? 43 : $values.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ExecutingFlowNodeFilterDataDto)) {
      return false;
    }
    final ExecutingFlowNodeFilterDataDto other = (ExecutingFlowNodeFilterDataDto) o;
    if (!other.canEqual((Object) this)) {
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
    return "ExecutingFlowNodeFilterDataDto(values=" + getValues() + ")";
  }
}
