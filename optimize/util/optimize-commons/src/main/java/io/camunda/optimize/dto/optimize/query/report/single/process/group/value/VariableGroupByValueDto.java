/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.group.value;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import java.util.Objects;

public class VariableGroupByValueDto implements ProcessGroupByValueDto {

  protected String name;
  protected VariableType type;

  public VariableGroupByValueDto() {}

  @Override
  public boolean isCombinable(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VariableGroupByValueDto)) {
      return false;
    }
    final VariableGroupByValueDto that = (VariableGroupByValueDto) o;
    return Objects.equals(name, that.name) && Objects.equals(type, that.type);
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public VariableType getType() {
    return type;
  }

  public void setType(final VariableType type) {
    this.type = type;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof VariableGroupByValueDto;
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
    return "VariableGroupByValueDto(name=" + getName() + ", type=" + getType() + ")";
  }
}
