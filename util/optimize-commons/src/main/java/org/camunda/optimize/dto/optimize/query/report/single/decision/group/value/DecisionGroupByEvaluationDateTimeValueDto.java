/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.group.value;

import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;

import java.util.Objects;

@Data
public class DecisionGroupByEvaluationDateTimeValueDto implements DecisionGroupByValueDto {

  protected AggregateByDateUnit unit;

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DecisionGroupByEvaluationDateTimeValueDto)) {
      return false;
    }
    DecisionGroupByEvaluationDateTimeValueDto that = (DecisionGroupByEvaluationDateTimeValueDto) o;
    return Objects.equals(unit, that.unit);
  }
}
