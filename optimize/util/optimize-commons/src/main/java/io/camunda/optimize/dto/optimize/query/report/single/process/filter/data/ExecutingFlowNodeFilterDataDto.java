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
import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ExecutingFlowNodeFilterDataDto that = (ExecutingFlowNodeFilterDataDto) o;
    return Objects.equals(values, that.values);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(values);
  }

  @Override
  public String toString() {
    return "ExecutingFlowNodeFilterDataDto(values=" + getValues() + ")";
  }
}
