/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.result.hyper;

import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
public class ReportHyperMapResultDto implements ProcessReportResultDto {

  private long instanceCount;
  private long instanceCountWithoutFilters;
  private List<HyperMapResultEntryDto> data = new ArrayList<>();

  public Optional<HyperMapResultEntryDto> getDataEntryForKey(final String key) {
    return data.stream().filter(entry -> key.equals(entry.getKey())).findFirst();
  }

  @Override
  public ResultType getType() {
    return ResultType.HYPER_MAP;
  }

  @Override
  public void sortResultData(final ReportSortingDto sortingDto, final boolean keyIsOfNumericType) {
    Optional.of(sortingDto).ifPresent(
      sorting -> data
        .forEach(groupByEntry -> groupByEntry.sortResultData(sorting, keyIsOfNumericType))
    );
  }
}
