/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.group.value;

import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;

import java.util.Objects;

public class DateGroupByValueDto implements ProcessGroupByValueDto {

  @Getter @Setter protected GroupByDateUnit unit;

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DateGroupByValueDto)) {
      return false;
    }
    DateGroupByValueDto that = (DateGroupByValueDto) o;
    return Objects.equals(unit, that.unit);
  }
}
