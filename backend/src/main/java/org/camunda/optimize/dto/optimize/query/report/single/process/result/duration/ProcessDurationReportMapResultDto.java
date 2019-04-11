/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.result.duration;

import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.LimitedResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProcessDurationReportMapResultDto extends ProcessReportResultDto implements LimitedResultDto {

  private List<MapResultEntryDto<AggregationResultDto>> data = new ArrayList<>();
  private Boolean isComplete = true;

  public Optional<MapResultEntryDto<AggregationResultDto>> getDataEntryForKey(final String key) {
    return data.stream().filter(entry -> key.equals(entry.getKey())).findFirst();
  }

  @Override
  public Boolean getIsComplete() {
    return isComplete;
  }

  public void setComplete(final Boolean complete) {
    isComplete = complete;
  }

  public List<MapResultEntryDto<AggregationResultDto>> getData() {
    return data;
  }

  public void setData(List<MapResultEntryDto<AggregationResultDto>> data) {
    this.data = data;
  }

  @Override
  public ResultType getType() {
    return ResultType.DURATION_MAP;
  }
}
