/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.result;

import org.camunda.optimize.dto.optimize.query.report.single.result.LimitedResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProcessReportMapResultDto extends ProcessReportResultDto implements LimitedResultDto {

  private List<MapResultEntryDto<Long>> data = new ArrayList<>();
  private Boolean isComplete = true;

  public Optional<MapResultEntryDto<Long>> getDataEntryForKey(final String key) {
    return data.stream().filter(entry -> key.equals(entry.getKey())).findFirst();
  }

  @Override
  public Boolean getIsComplete() {
    return isComplete;
  }

  public void setComplete(final Boolean complete) {
    isComplete = complete;
  }

  public List<MapResultEntryDto<Long>> getData() {
    return data;
  }

  public void setData(List<MapResultEntryDto<Long>> data) {
    this.data = data;
  }

  @Override
  public ResultType getType() {
    return ResultType.FREQUENCY_MAP;
  }
}
