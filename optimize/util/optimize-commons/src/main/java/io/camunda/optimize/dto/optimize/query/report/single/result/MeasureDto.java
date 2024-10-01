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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $property = getProperty();
    result = result * PRIME + ($property == null ? 43 : $property.hashCode());
    final Object $aggregationType = getAggregationType();
    result = result * PRIME + ($aggregationType == null ? 43 : $aggregationType.hashCode());
    final Object $userTaskDurationTime = getUserTaskDurationTime();
    result =
        result * PRIME + ($userTaskDurationTime == null ? 43 : $userTaskDurationTime.hashCode());
    final Object $data = getData();
    result = result * PRIME + ($data == null ? 43 : $data.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof MeasureDto)) {
      return false;
    }
    final MeasureDto<?> other = (MeasureDto<?>) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$property = getProperty();
    final Object other$property = other.getProperty();
    if (this$property == null ? other$property != null : !this$property.equals(other$property)) {
      return false;
    }
    final Object this$aggregationType = getAggregationType();
    final Object other$aggregationType = other.getAggregationType();
    if (this$aggregationType == null
        ? other$aggregationType != null
        : !this$aggregationType.equals(other$aggregationType)) {
      return false;
    }
    final Object this$userTaskDurationTime = getUserTaskDurationTime();
    final Object other$userTaskDurationTime = other.getUserTaskDurationTime();
    if (this$userTaskDurationTime == null
        ? other$userTaskDurationTime != null
        : !this$userTaskDurationTime.equals(other$userTaskDurationTime)) {
      return false;
    }
    final Object this$data = getData();
    final Object other$data = other.getData();
    if (this$data == null ? other$data != null : !this$data.equals(other$data)) {
      return false;
    }
    return true;
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
