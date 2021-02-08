/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;

import static org.camunda.optimize.dto.optimize.ReportConstants.HYPER_MAP_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.MAP_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.NUMBER_RESULT_TYPE;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ReportMapResultDto.class, name = MAP_RESULT_TYPE),
  @JsonSubTypes.Type(value = ReportHyperMapResultDto.class, name = HYPER_MAP_RESULT_TYPE),
  @JsonSubTypes.Type(value = NumberResultDto.class, name = NUMBER_RESULT_TYPE),
})
public interface SingleReportResultDto extends ReportResultDto {

  long getInstanceCount();

  void setInstanceCount(long instanceCount);

  long getInstanceCountWithoutFilters();

  void setInstanceCountWithoutFilters(long instanceCount);
}
