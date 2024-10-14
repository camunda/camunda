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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $data = getData();
    result = result * PRIME + ($data == null ? 43 : $data.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof MultipleVariableFilterDataDto)) {
      return false;
    }
    final MultipleVariableFilterDataDto other = (MultipleVariableFilterDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$data = getData();
    final Object other$data = other.getData();
    if (this$data == null ? other$data != null : !this$data.equals(other$data)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "MultipleVariableFilterDataDto(data=" + getData() + ")";
  }
}
