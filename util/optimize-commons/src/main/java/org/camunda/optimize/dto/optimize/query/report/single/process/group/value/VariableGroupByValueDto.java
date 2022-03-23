/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.group.value;

import lombok.Data;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.Objects;

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
    return Objects.equals(name, that.name) &&
      Objects.equals(type, that.type);
  }
}
