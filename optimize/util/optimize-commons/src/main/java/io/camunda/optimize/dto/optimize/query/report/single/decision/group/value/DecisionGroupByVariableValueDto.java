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

public class DecisionGroupByVariableValueDto implements DecisionGroupByValueDto {

  protected String id;
  protected String name;
  protected VariableType type;

  public Optional<String> getName() {
    return Optional.ofNullable(name);
  }

  public void setName(final String name) {
    this.name = name;
  }

  @Override
  public boolean isCombinable(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DecisionGroupByVariableValueDto)) {
      return false;
    }
    final DecisionGroupByVariableValueDto that = (DecisionGroupByVariableValueDto) o;
    return Objects.equals(id, that.id);
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public VariableType getType() {
    return type;
  }

  public void setType(final VariableType type) {
    this.type = type;
  }
}
