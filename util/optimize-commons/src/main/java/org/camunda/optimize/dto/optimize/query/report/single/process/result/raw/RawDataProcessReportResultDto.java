/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.result.raw;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
public class RawDataProcessReportResultDto implements ProcessReportResultDto {

  private long instanceCount;
  private long instanceCountWithoutFilters;
  private List<MeasureDto<List<RawDataProcessInstanceDto>>> measures = new ArrayList<>();
  private PaginationDto pagination = new PaginationDto(0, 0);

  @Override
  public ResultType getType() {
    return ResultType.RAW;
  }

  public void addMeasureData(final List<RawDataProcessInstanceDto> measure) {
    this.measures.add(MeasureDto.of(ViewProperty.RAW_DATA, measure));
  }

  @JsonIgnore
  public List<RawDataProcessInstanceDto> getData() {
    return measures.stream().findFirst().map(MeasureDto::getData).orElse(Collections.emptyList());
  }

}
