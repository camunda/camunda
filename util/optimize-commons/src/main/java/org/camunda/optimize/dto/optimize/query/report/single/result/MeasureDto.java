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
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeasureDto<T> {
  private ViewProperty property;
  private AggregationType aggregationType;
  private UserTaskDurationTime userTaskDurationTime;
  private T data;

  public static <T> MeasureDto<T> of(ViewProperty property,
                                     AggregationType aggregationType,
                                     UserTaskDurationTime userTaskDurationTime,
                                     T data) {
    return new MeasureDto<>(property, aggregationType, userTaskDurationTime, data);
  }

  public static <T> MeasureDto<T> of(ViewProperty property, T data) {
    return new MeasureDto<>(property, null, null, data);
  }
}
