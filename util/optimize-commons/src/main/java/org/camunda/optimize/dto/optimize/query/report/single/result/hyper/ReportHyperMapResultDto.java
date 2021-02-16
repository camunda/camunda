/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.result.hyper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
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

  public Optional<HyperMapResultEntryDto> getDataEntryForKey(final String key) {
    return getFirstMeasureData().stream().filter(entry -> key.equals(entry.getKey())).findFirst();
  }

  @JsonIgnore
  public List<HyperMapResultEntryDto> getFirstMeasureData() {
    return  measures.stream().findFirst().map(MeasureDto::getData).orElse(null);
  }
}
