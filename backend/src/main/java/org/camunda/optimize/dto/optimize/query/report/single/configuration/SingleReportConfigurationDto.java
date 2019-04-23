/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration;

import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.heatmap_target_value.HeatmapTargetValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.SingleReportTargetValueDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
public class SingleReportConfigurationDto {

  private String color = ReportConstants.DEFAULT_CONFIGURATION_COLOR;
  private AggregationType aggregationType = AggregationType.AVERAGE;
  private Boolean showInstanceCount = false;
  private Boolean pointMarkers = true;
  private Integer precision = null;
  private Boolean hideRelativeValue = false;
  private Boolean hideAbsoluteValue = false;
  private String yLabel = "";
  private String xLabel = "";
  private Boolean alwaysShowRelative = false;
  private Boolean alwaysShowAbsolute = false;
  private Boolean showGradientBars = true;
  private String xml = null;
  private List<String> excludedColumns = new ArrayList<>();
  private ColumnOrderDto columnOrder = new ColumnOrderDto();
  private SingleReportTargetValueDto targetValue = new SingleReportTargetValueDto();
  private HeatmapTargetValueDto heatmapTargetValue = new HeatmapTargetValueDto();

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SingleReportConfigurationDto)) {
      return false;
    }
    SingleReportConfigurationDto that = (SingleReportConfigurationDto) o;
    return Objects.equals(color, that.color) &&
      Objects.equals(aggregationType, that.aggregationType) &&
      Objects.equals(showInstanceCount, that.showInstanceCount) &&
      Objects.equals(pointMarkers, that.pointMarkers) &&
      Objects.equals(precision, that.precision) &&
      Objects.equals(hideRelativeValue, that.hideRelativeValue) &&
      Objects.equals(hideAbsoluteValue, that.hideAbsoluteValue) &&
      Objects.equals(yLabel, that.yLabel) &&
      Objects.equals(xLabel, that.xLabel) &&
      Objects.equals(alwaysShowRelative, that.alwaysShowRelative) &&
      Objects.equals(alwaysShowAbsolute, that.alwaysShowAbsolute) &&
      Objects.equals(showGradientBars, that.showGradientBars) &&
      Objects.equals(xml, that.xml) &&
      Objects.equals(excludedColumns, that.excludedColumns) &&
      Objects.equals(columnOrder, that.columnOrder) &&
      Objects.equals(targetValue, that.targetValue) &&
      Objects.equals(heatmapTargetValue, that.heatmapTargetValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      color,
      aggregationType,
      showInstanceCount,
      pointMarkers,
      precision,
      hideRelativeValue,
      hideAbsoluteValue,
      yLabel,
      xLabel,
      alwaysShowRelative,
      alwaysShowAbsolute,
      showGradientBars,
      xml,
      excludedColumns,
      columnOrder,
      targetValue,
      heatmapTargetValue
    );
  }
}
