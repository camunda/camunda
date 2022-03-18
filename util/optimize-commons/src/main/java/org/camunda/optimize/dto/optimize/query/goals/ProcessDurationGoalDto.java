/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.goals;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
@FieldNameConstants
@AllArgsConstructor
@NoArgsConstructor
public class ProcessDurationGoalDto implements OptimizeDto {

  @NotNull
  private DurationGoalType type;
  @Min(0)
  @Max(100)
  private double percentile;
  @Min(0)
  private long value;
  @NotNull
  private DurationUnit unit;

}
