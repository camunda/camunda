/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.view;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import java.util.Objects;

public class VariableViewPropertyDto implements TypedViewPropertyDto {

  private final String name;
  private final VariableType type;

  public VariableViewPropertyDto(final String name, final VariableType type) {
    this.name = name;
    this.type = type;
  }

  @Override
  public boolean isCombinable(final Object o) {
    return o instanceof VariableViewPropertyDto;
  }

  public String getName() {
    return name;
  }

  public VariableType getType() {
    return type;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof VariableViewPropertyDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final VariableViewPropertyDto that = (VariableViewPropertyDto) o;
    return Objects.equals(name, that.name) && type == that.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type);
  }

  @Override
  public String toString() {
    return "aggregation";
  }
}
