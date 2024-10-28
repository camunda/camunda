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
      final ViewProperty property,
      final AggregationDto aggregationType,
      final UserTaskDurationTime userTaskDurationTime,
      final T data,
      final ResultType type) {
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

  public void setProperty(final ViewProperty property) {
    this.property = property;
  }

  public void setAggregationType(final AggregationDto aggregationType) {
    this.aggregationType = aggregationType;
  }

  public void setUserTaskDurationTime(final UserTaskDurationTime userTaskDurationTime) {
    this.userTaskDurationTime = userTaskDurationTime;
  }

  public void setData(final T data) {
    this.data = data;
  }

  public void setType(final ResultType type) {
    this.type = type;
  }

  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof MeasureResponseDto;
  }

  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
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
