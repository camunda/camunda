/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.result;

import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
public class ReportMapResultDto implements LimitedResultDto, DecisionReportResultDto, ProcessReportResultDto {

  private List<MapResultEntryDto<Long>> data = new ArrayList<>();
  private Boolean isComplete = true;
  private long instanceCount;

  public Optional<MapResultEntryDto<Long>> getEntryForKey(final String key) {
    return data.stream().filter(entry -> key.equals(entry.getKey())).findFirst();
  }

  @Override
  public ResultType getType() {
    return ResultType.MAP;
  }
}
