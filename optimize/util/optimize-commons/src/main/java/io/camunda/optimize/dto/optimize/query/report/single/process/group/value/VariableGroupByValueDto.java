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
import lombok.Data;

@Data
public class VariableGroupByValueDto implements ProcessGroupByValueDto {

  protected String name;
  protected VariableType type;

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VariableGroupByValueDto)) {
      return false;
    }
    VariableGroupByValueDto that = (VariableGroupByValueDto) o;
    return Objects.equals(name, that.name) && Objects.equals(type, that.type);
  }
}
