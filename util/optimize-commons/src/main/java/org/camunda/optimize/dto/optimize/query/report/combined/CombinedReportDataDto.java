/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.combined;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.configuration.CombinedReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public class CombinedReportDataDto implements ReportDataDto {

  protected CombinedReportConfigurationDto configuration = new CombinedReportConfigurationDto();
  protected ProcessVisualization visualization;
  protected List<CombinedReportItemDto> reports = new ArrayList<>();

  @JsonIgnore
  public List<String> getReportIds() {
    return Optional.ofNullable(reports)
      .map(Collection::stream)
      .map(reportIdStream -> reportIdStream.map(CombinedReportItemDto::getId).collect(Collectors.toList()))
      .orElse(new ArrayList<>());
  }

  @Override
  public String createCommandKey() {
    return "combined";
  }
}
