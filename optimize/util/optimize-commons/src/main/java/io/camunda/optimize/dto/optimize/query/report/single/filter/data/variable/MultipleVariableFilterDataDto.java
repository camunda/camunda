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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "MultipleVariableFilterDataDto(data=" + getData() + ")";
  }
}
