/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.experimental.SuperBuilder;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;

import java.util.List;

import static java.util.stream.Collectors.toList;

@SuperBuilder
public class SingleProcessReportDefinitionRequestDto extends SingleReportDefinitionDto<ProcessReportDataDto> {

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
    return data.getFilter().stream()
      .map(ProcessFilterDto::getData)
      .collect(toList());
  }
}
