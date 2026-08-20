/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import java.util.List;
import java.util.Objects;

public class MultipleVariableFilterDataDto implements FilterDataDto {

  protected List<VariableFilterDataDto<?>> data;

  public MultipleVariableFilterDataDto(final List<VariableFilterDataDto<?>> data) {
    this.data = data;
  }

  public MultipleVariableFilterDataDto() {}

  public List<VariableFilterDataDto<?>> getData() {
    return data;
  }

  public void setData(final List<VariableFilterDataDto<?>> data) {
    this.data = data;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof MultipleVariableFilterDataDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MultipleVariableFilterDataDto that = (MultipleVariableFilterDataDto) o;
    return Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(data);
  }

  @Override
  public String toString() {
    return "MultipleVariableFilterDataDto(data=" + getData() + ")";
  }
}
