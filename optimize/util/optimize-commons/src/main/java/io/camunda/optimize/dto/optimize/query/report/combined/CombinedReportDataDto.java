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

public class CombinedReportDataDto implements ReportDataDto {

  protected CombinedReportConfigurationDto configuration = new CombinedReportConfigurationDto();
  protected ProcessVisualization visualization;
  protected List<CombinedReportItemDto> reports = new ArrayList<>();

  public CombinedReportDataDto(
      final CombinedReportConfigurationDto configuration,
      final ProcessVisualization visualization,
      final List<CombinedReportItemDto> reports) {
    this.configuration = configuration;
    this.visualization = visualization;
    this.reports = reports;
  }

  public CombinedReportDataDto() {}

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

  public CombinedReportConfigurationDto getConfiguration() {
    return configuration;
  }

  public void setConfiguration(final CombinedReportConfigurationDto configuration) {
    this.configuration = configuration;
  }

  public ProcessVisualization getVisualization() {
    return visualization;
  }

  public void setVisualization(final ProcessVisualization visualization) {
    this.visualization = visualization;
  }

  public List<CombinedReportItemDto> getReports() {
    return reports;
  }

  public void setReports(final List<CombinedReportItemDto> reports) {
    this.reports = reports;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CombinedReportDataDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $configuration = getConfiguration();
    result = result * PRIME + ($configuration == null ? 43 : $configuration.hashCode());
    final Object $visualization = getVisualization();
    result = result * PRIME + ($visualization == null ? 43 : $visualization.hashCode());
    final Object $reports = getReports();
    result = result * PRIME + ($reports == null ? 43 : $reports.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CombinedReportDataDto)) {
      return false;
    }
    final CombinedReportDataDto other = (CombinedReportDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$configuration = getConfiguration();
    final Object other$configuration = other.getConfiguration();
    if (this$configuration == null
        ? other$configuration != null
        : !this$configuration.equals(other$configuration)) {
      return false;
    }
    final Object this$visualization = getVisualization();
    final Object other$visualization = other.getVisualization();
    if (this$visualization == null
        ? other$visualization != null
        : !this$visualization.equals(other$visualization)) {
      return false;
    }
    final Object this$reports = getReports();
    final Object other$reports = other.getReports();
    if (this$reports == null ? other$reports != null : !this$reports.equals(other$reports)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CombinedReportDataDto(configuration="
        + getConfiguration()
        + ", visualization="
        + getVisualization()
        + ", reports="
        + getReports()
        + ")";
  }

  public static final class Fields {

    public static final String configuration = "configuration";
    public static final String visualization = "visualization";
    public static final String reports = "reports";
  }
}
