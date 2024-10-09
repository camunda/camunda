/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.report.measure;

import static io.camunda.optimize.dto.optimize.ReportConstants.HYPER_MAP_RESULT_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.MAP_RESULT_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.NUMBER_RESULT_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.RAW_RESULT_TYPE;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import io.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = MapMeasureResponseDto.class, name = MAP_RESULT_TYPE),
  @JsonSubTypes.Type(value = HyperMapMeasureResponseDto.class, name = HYPER_MAP_RESULT_TYPE),
  @JsonSubTypes.Type(value = NumberMeasureResponseDto.class, name = NUMBER_RESULT_TYPE),
  @JsonSubTypes.Type(value = RawDataMeasureResponseDto.class, name = RAW_RESULT_TYPE),
})
public class MeasureResponseDto<T> {

  private ViewProperty property;
  private AggregationDto aggregationType;
  private UserTaskDurationTime userTaskDurationTime;
  private T data;
  private ResultType type;

  public MeasureResponseDto(
      ViewProperty property,
      AggregationDto aggregationType,
      UserTaskDurationTime userTaskDurationTime,
      T data,
      ResultType type) {
    this.property = property;
    this.aggregationType = aggregationType;
    this.userTaskDurationTime = userTaskDurationTime;
    this.data = data;
    this.type = type;
  }

  protected MeasureResponseDto() {}

  public ViewProperty getProperty() {
    return this.property;
  }

  public AggregationDto getAggregationType() {
    return this.aggregationType;
  }

  public UserTaskDurationTime getUserTaskDurationTime() {
    return this.userTaskDurationTime;
  }

  public T getData() {
    return this.data;
  }

  public ResultType getType() {
    return this.type;
  }

  public void setProperty(ViewProperty property) {
    this.property = property;
  }

  public void setAggregationType(AggregationDto aggregationType) {
    this.aggregationType = aggregationType;
  }

  public void setUserTaskDurationTime(UserTaskDurationTime userTaskDurationTime) {
    this.userTaskDurationTime = userTaskDurationTime;
  }

  public void setData(T data) {
    this.data = data;
  }

  public void setType(ResultType type) {
    this.type = type;
  }

  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof MeasureResponseDto)) {
      return false;
    }
    final MeasureResponseDto<?> other = (MeasureResponseDto<?>) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$property = this.getProperty();
    final Object other$property = other.getProperty();
    if (this$property == null ? other$property != null : !this$property.equals(other$property)) {
      return false;
    }
    final Object this$aggregationType = this.getAggregationType();
    final Object other$aggregationType = other.getAggregationType();
    if (this$aggregationType == null
        ? other$aggregationType != null
        : !this$aggregationType.equals(other$aggregationType)) {
      return false;
    }
    final Object this$userTaskDurationTime = this.getUserTaskDurationTime();
    final Object other$userTaskDurationTime = other.getUserTaskDurationTime();
    if (this$userTaskDurationTime == null
        ? other$userTaskDurationTime != null
        : !this$userTaskDurationTime.equals(other$userTaskDurationTime)) {
      return false;
    }
    final Object this$data = this.getData();
    final Object other$data = other.getData();
    if (this$data == null ? other$data != null : !this$data.equals(other$data)) {
      return false;
    }
    final Object this$type = this.getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof MeasureResponseDto;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $property = this.getProperty();
    result = result * PRIME + ($property == null ? 43 : $property.hashCode());
    final Object $aggregationType = this.getAggregationType();
    result = result * PRIME + ($aggregationType == null ? 43 : $aggregationType.hashCode());
    final Object $userTaskDurationTime = this.getUserTaskDurationTime();
    result =
        result * PRIME + ($userTaskDurationTime == null ? 43 : $userTaskDurationTime.hashCode());
    final Object $data = this.getData();
    result = result * PRIME + ($data == null ? 43 : $data.hashCode());
    final Object $type = this.getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    return result;
  }

  public String toString() {
    return "MeasureResponseDto(property="
        + this.getProperty()
        + ", aggregationType="
        + this.getAggregationType()
        + ", userTaskDurationTime="
        + this.getUserTaskDurationTime()
        + ", data="
        + this.getData()
        + ", type="
        + this.getType()
        + ")";
  }
}
