/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.combined;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.combined.configuration.CombinedReportConfigurationDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CombinedReportDataDto implements ReportDataDto {

  protected CombinedReportConfigurationDto configuration = new CombinedReportConfigurationDto();
  protected ProcessVisualization visualization;
  protected List<CombinedReportItemDto> reports = new ArrayList<>();

  @JsonIgnore
  public List<String> getReportIds() {
    return Optional.ofNullable(reports)
        .map(Collection::stream)
        .map(
            reportIdStream ->
                reportIdStream.map(CombinedReportItemDto::getId).collect(Collectors.toList()))
        .orElse(new ArrayList<>());
  }

  @Override
  public String createCommandKey() {
    return "combined";
  }

  @Override
  public List<String> createCommandKeys() {
    return Collections.singletonList(createCommandKey());
  }

  public static final class Fields {

    public static final String configuration = "configuration";
    public static final String visualization = "visualization";
    public static final String reports = "reports";
  }
}
