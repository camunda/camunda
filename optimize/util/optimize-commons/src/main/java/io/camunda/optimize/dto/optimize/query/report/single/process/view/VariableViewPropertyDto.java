/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.view;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import lombok.Data;

@Data
public class VariableViewPropertyDto implements TypedViewPropertyDto {

  private final String name;
  private final VariableType type;

  public VariableViewPropertyDto(String name, VariableType type) {
    this.name = name;
    this.type = type;
  }

  @Override
  public String toString() {
    return "aggregation";
  }

  @Override
  public boolean isCombinable(final Object o) {
    return o instanceof VariableViewPropertyDto;
  }
}
