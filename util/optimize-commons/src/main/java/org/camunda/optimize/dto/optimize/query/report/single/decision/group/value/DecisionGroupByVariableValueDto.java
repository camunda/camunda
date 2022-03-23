/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.group.value;

import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.Objects;
import java.util.Optional;

public class DecisionGroupByVariableValueDto implements DecisionGroupByValueDto {

  @Getter @Setter protected String id;
  @Setter protected String name;
  @Getter @Setter
  protected VariableType type;

  public Optional<String> getName() {
    return Optional.ofNullable(name);
  }

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DecisionGroupByVariableValueDto)) {
      return false;
    }
    DecisionGroupByVariableValueDto that = (DecisionGroupByVariableValueDto) o;
    return Objects.equals(id, that.id);
  }
}
