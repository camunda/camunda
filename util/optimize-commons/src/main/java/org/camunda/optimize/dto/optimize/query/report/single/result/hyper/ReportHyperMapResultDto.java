/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.result.hyper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReportHyperMapResultDto implements ProcessReportResultDto {

  private long instanceCount;
  private long instanceCountWithoutFilters;
  private List<MeasureDto<List<HyperMapResultEntryDto>>> measures = new ArrayList<>();

  @Override
  public ResultType getType() {
    return ResultType.HYPER_MAP;
  }

  public void addMeasure(MeasureDto<List<HyperMapResultEntryDto>> measure) {
    this.measures.add(measure);
  }

  @JsonIgnore
  public List<HyperMapResultEntryDto> getFirstMeasureData() {
    return  measures.stream().findFirst().map(MeasureDto::getData).orElse(Collections.emptyList());
  }
}
