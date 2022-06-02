/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.group.value;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;

import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DateGroupByValueDto implements ProcessGroupByValueDto {

  protected AggregateByDateUnit unit;

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
