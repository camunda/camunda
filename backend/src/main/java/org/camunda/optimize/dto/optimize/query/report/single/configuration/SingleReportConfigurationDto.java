/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.heatmap_target_value.HeatmapTargetValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.SingleReportTargetValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;

import java.util.ArrayList;
import java.util.List;


@Data
public class SingleReportConfigurationDto {
  private String color = ReportConstants.DEFAULT_CONFIGURATION_COLOR;
  private AggregationType aggregationType = AggregationType.AVERAGE;
  private FlowNodeExecutionState flowNodeExecutionState = FlowNodeExecutionState.ALL;
  private UserTaskDurationTime userTaskDurationTime = UserTaskDurationTime.TOTAL;
  private List<String> hiddenNodes = new ArrayList<>();
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

  @JsonIgnore
  public String createCommandKey(ProcessViewDto viewDto) {
    String configurationCommandKey = "";
    List<String> configsToConsiderForCommand = new ArrayList<>();
    if (viewDto != null && viewDto.getProperty() != null &&
        viewDto.getProperty().equals(ProcessViewProperty.DURATION)) {
      configsToConsiderForCommand.add(this.aggregationType.getId());
    }
    if (viewDto != null && viewDto.getEntity() != null &&
        viewDto.getEntity().equals(ProcessViewEntity.USER_TASK)) {
      configsToConsiderForCommand.add(this.userTaskDurationTime.getId());
    }
    return String.join("-", configsToConsiderForCommand);
  }
}
