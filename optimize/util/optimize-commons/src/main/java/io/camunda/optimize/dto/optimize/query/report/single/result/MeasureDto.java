/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.result;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeasureDto<T> {
  private ViewProperty property;
  private AggregationDto aggregationType;
  private UserTaskDurationTime userTaskDurationTime;
  private T data;

  public static <T> MeasureDto<T> of(
      ViewProperty property,
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
