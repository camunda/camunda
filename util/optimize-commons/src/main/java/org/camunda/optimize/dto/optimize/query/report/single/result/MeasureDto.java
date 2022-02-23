/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.result;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeasureDto<T> {
  private ViewProperty property;
  private AggregationDto aggregationType;
  private UserTaskDurationTime userTaskDurationTime;
  private T data;

  public static <T> MeasureDto<T> of(ViewProperty property,
                                     AggregationDto aggregationType,
                                     UserTaskDurationTime userTaskDurationTime,
                                     T data) {
    return new MeasureDto<>(property, aggregationType, userTaskDurationTime, data);
  }

  public static <T> MeasureDto<T> of(ViewProperty property, T data) {
    return new MeasureDto<>(property, null, null, data);
  }

  public static <T> MeasureDto<T> of(T data) {
    return new MeasureDto<>(null, null, null, data);
  }
}
