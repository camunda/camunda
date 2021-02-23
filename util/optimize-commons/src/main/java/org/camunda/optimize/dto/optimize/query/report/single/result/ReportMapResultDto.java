/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReportMapResultDto implements DecisionReportResultDto, ProcessReportResultDto {

  private long instanceCount;
  private long instanceCountWithoutFilters;
  private List<MeasureDto<List<MapResultEntryDto>>> measures = new ArrayList<>();

  @Override
  public ResultType getType() {
    return ResultType.MAP;
  }

  public void addMeasure(MeasureDto<List<MapResultEntryDto>> measure) {
    this.measures.add(measure);
  }

  @JsonIgnore
  public List<MapResultEntryDto> getFirstMeasureData() {
    return measures.stream().findFirst().map(MeasureDto::getData).orElse(Collections.emptyList());
  }
}
