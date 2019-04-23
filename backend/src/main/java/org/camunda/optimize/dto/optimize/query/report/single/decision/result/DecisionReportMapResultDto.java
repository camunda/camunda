/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.result;

import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.single.result.LimitedResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
@Setter
public class DecisionReportMapResultDto extends DecisionReportResultDto implements LimitedResultDto {

  private List<MapResultEntryDto<Long>> data = new ArrayList<>();
  private Boolean isComplete = true;

  public Optional<MapResultEntryDto<Long>> getEntryForKey(final String key) {
    return data.stream().filter(entry -> key.equals(entry.getKey())).findFirst();
  }

  @Override
  public ResultType getType() {
    return ResultType.FREQUENCY_MAP;
  }
}
