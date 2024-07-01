/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.decision.group.value;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;

public class DecisionGroupByVariableValueDto implements DecisionGroupByValueDto {

  @Getter @Setter protected String id;
  @Setter protected String name;
  @Getter @Setter protected VariableType type;

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
