/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.report.measure;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import static org.camunda.optimize.dto.optimize.ReportConstants.HYPER_MAP_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.MAP_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.NUMBER_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.RAW_RESULT_TYPE;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = MapMeasureResponseDto.class, name = MAP_RESULT_TYPE),
  @JsonSubTypes.Type(value = HyperMapMeasureResponseDto.class, name = HYPER_MAP_RESULT_TYPE),
  @JsonSubTypes.Type(value = NumberMeasureResponseDto.class, name = NUMBER_RESULT_TYPE),
  @JsonSubTypes.Type(value = RawDataMeasureResponseDto.class, name = RAW_RESULT_TYPE),
})
@SuperBuilder
public class MeasureResponseDto<T> {
  private ViewProperty property;
  private AggregationDto aggregationType;
  private UserTaskDurationTime userTaskDurationTime;
  private T data;
  private ResultType type;
}
