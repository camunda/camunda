/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data;

import java.util.List;

public class BooleanVariableFilterSubDataDto {

  protected List<Boolean> values;

  public BooleanVariableFilterSubDataDto(final List<Boolean> values) {
    this.values = values;
  }

  protected BooleanVariableFilterSubDataDto() {}

  public List<Boolean> getValues() {
    return values;
  }

  public void setValues(final List<Boolean> values) {
    this.values = values;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof BooleanVariableFilterSubDataDto;
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
    return "BooleanVariableFilterSubDataDto(values=" + getValues() + ")";
  }
}
