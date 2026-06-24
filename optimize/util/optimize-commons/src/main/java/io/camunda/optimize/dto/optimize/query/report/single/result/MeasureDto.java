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
import java.util.Objects;

public class MeasureDto<T> {

  private ViewProperty property;
  private AggregationDto aggregationType;
  private UserTaskDurationTime userTaskDurationTime;
  private T data;

  public MeasureDto(
      final ViewProperty property,
      final AggregationDto aggregationType,
      final UserTaskDurationTime userTaskDurationTime,
      final T data) {
    this.property = property;
    this.aggregationType = aggregationType;
    this.userTaskDurationTime = userTaskDurationTime;
    this.data = data;
  }

  protected MeasureDto() {}

  public static <T> MeasureDto<T> of(
      final ViewProperty property,
      final AggregationDto aggregationType,
      final UserTaskDurationTime userTaskDurationTime,
      final T data) {
    return new MeasureDto<>(property, aggregationType, userTaskDurationTime, data);
  }

  public static <T> MeasureDto<T> of(final ViewProperty property, final T data) {
    return new MeasureDto<>(property, null, null, data);
  }

  public static <T> MeasureDto<T> of(final T data) {
    return new MeasureDto<>(null, null, null, data);
  }

  public ViewProperty getProperty() {
    return property;
  }

  public void setProperty(final ViewProperty property) {
    this.property = property;
  }

  public AggregationDto getAggregationType() {
    return aggregationType;
  }

  public void setAggregationType(final AggregationDto aggregationType) {
    this.aggregationType = aggregationType;
  }

  public UserTaskDurationTime getUserTaskDurationTime() {
    return userTaskDurationTime;
  }

  public void setUserTaskDurationTime(final UserTaskDurationTime userTaskDurationTime) {
    this.userTaskDurationTime = userTaskDurationTime;
  }

  public T getData() {
    return data;
  }

  public void setData(final T data) {
    this.data = data;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof MeasureDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MeasureDto<?> that = (MeasureDto<?>) o;
    return Objects.equals(property, that.property)
        && Objects.equals(aggregationType, that.aggregationType)
        && userTaskDurationTime == that.userTaskDurationTime
        && Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(property, aggregationType, userTaskDurationTime, data);
  }

  @Override
  public String toString() {
    return "MeasureDto(property="
        + getProperty()
        + ", aggregationType="
        + getAggregationType()
        + ", userTaskDurationTime="
        + getUserTaskDurationTime()
        + ", data="
        + getData()
        + ")";
  }
}
