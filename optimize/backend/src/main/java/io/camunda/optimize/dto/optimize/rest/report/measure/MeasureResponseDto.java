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

  protected MeasureResponseDto(final MeasureResponseDtoBuilder<T, ?, ?> b) {
    property = b.property;
    aggregationType = b.aggregationType;
    userTaskDurationTime = b.userTaskDurationTime;
    data = b.data;
    type = b.type;
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

  public ResultType getType() {
    return type;
  }

  public void setType(final ResultType type) {
    this.type = type;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof MeasureResponseDto;
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
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    return result;
  }

  @Override
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
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "MeasureResponseDto(property="
        + getProperty()
        + ", aggregationType="
        + getAggregationType()
        + ", userTaskDurationTime="
        + getUserTaskDurationTime()
        + ", data="
        + getData()
        + ", type="
        + getType()
        + ")";
  }

  public static <T> MeasureResponseDtoBuilder<T, ?, ?> builder() {
    return new MeasureResponseDtoBuilderImpl<T>();
  }

  public abstract static class MeasureResponseDtoBuilder<
      T, C extends MeasureResponseDto<T>, B extends MeasureResponseDtoBuilder<T, C, B>> {

    private ViewProperty property;
    private AggregationDto aggregationType;
    private UserTaskDurationTime userTaskDurationTime;
    private T data;
    private ResultType type;

    public B property(final ViewProperty property) {
      this.property = property;
      return self();
    }

    public B aggregationType(final AggregationDto aggregationType) {
      this.aggregationType = aggregationType;
      return self();
    }

    public B userTaskDurationTime(final UserTaskDurationTime userTaskDurationTime) {
      this.userTaskDurationTime = userTaskDurationTime;
      return self();
    }

    public B data(final T data) {
      this.data = data;
      return self();
    }

    public B type(final ResultType type) {
      this.type = type;
      return self();
    }

    protected abstract B self();

    public abstract C build();

    @Override
    public String toString() {
      return "MeasureResponseDto.MeasureResponseDtoBuilder(property="
          + property
          + ", aggregationType="
          + aggregationType
          + ", userTaskDurationTime="
          + userTaskDurationTime
          + ", data="
          + data
          + ", type="
          + type
          + ")";
    }
  }

  private static final class MeasureResponseDtoBuilderImpl<T>
      extends MeasureResponseDtoBuilder<
          T, MeasureResponseDto<T>, MeasureResponseDtoBuilderImpl<T>> {

    private MeasureResponseDtoBuilderImpl() {}

    @Override
    protected MeasureResponseDtoBuilderImpl<T> self() {
      return this;
    }

    @Override
    public MeasureResponseDto<T> build() {
      return new MeasureResponseDto<T>(this);
    }
  }
}
