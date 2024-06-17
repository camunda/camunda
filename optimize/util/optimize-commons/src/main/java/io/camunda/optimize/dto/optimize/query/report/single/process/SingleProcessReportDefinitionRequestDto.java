/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process;

import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import java.util.List;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public class SingleProcessReportDefinitionRequestDto
    extends SingleReportDefinitionDto<ProcessReportDataDto> {

  public SingleProcessReportDefinitionRequestDto() {
    super(new ProcessReportDataDto(), false, ReportType.PROCESS);
  }

  public SingleProcessReportDefinitionRequestDto(final ProcessReportDataDto data) {
    super(data, false, ReportType.PROCESS);
  }

  @Override
  public ReportType getReportType() {
    return super.getReportType();
  }

  @JsonIgnore
  public List<FilterDataDto> getFilterData() {
    return data.getFilter().stream().map(ProcessFilterDto::getData).collect(toList());
  }
}
