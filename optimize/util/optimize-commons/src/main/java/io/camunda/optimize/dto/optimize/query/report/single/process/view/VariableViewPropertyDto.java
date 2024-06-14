/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.view;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VariableViewPropertyDto implements TypedViewPropertyDto {

  private final String name;
  private final VariableType type;

  @Override
  public String toString() {
    return "aggregation";
  }

  @Override
  public boolean isCombinable(final Object o) {
    return o instanceof VariableViewPropertyDto;
  }
}
