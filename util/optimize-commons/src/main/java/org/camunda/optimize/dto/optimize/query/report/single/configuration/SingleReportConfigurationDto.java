/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.heatmap_target_value.HeatmapTargetValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.SingleReportTargetValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.nonNull;

@Data
@FieldNameConstants(asEnum = true)
public class SingleReportConfigurationDto {
  private String color = ReportConstants.DEFAULT_CONFIGURATION_COLOR;
  private AggregationType aggregationType = AggregationType.AVERAGE;
  private FlowNodeExecutionState flowNodeExecutionState = FlowNodeExecutionState.ALL;
  private UserTaskDurationTime userTaskDurationTime = UserTaskDurationTime.TOTAL;
  private HiddenNodesDto hiddenNodes = new HiddenNodesDto();
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
  private DistributedBy distributedBy = DistributedBy.NONE;

  @JsonIgnore
  public String createCommandKey(ProcessViewDto viewDto, ProcessGroupByDto groupByDto) {
    final List<String> configsToConsiderForCommand = new ArrayList<>();
    if (isDurationCommand(viewDto)) {
      configsToConsiderForCommand.add(this.aggregationType.getId());
    }
    if (isUserTaskDurationCommand(viewDto)) {
      configsToConsiderForCommand.add(this.userTaskDurationTime.getId());
    }
    if (isUserTaskCommand(viewDto) && isGroupByAssigneeOrCandidateGroup(groupByDto)) {
      configsToConsiderForCommand.add(this.distributedBy.getId());
    }
    return String.join("-", configsToConsiderForCommand);
  }

  private boolean isUserTaskDurationCommand(ProcessViewDto viewDto) {
    return isUserTaskCommand(viewDto) && isDurationCommand(viewDto);
  }

  private boolean isDurationCommand(ProcessViewDto viewDto) {
    return nonNull(viewDto) && nonNull(viewDto.getProperty()) &&
      viewDto.getProperty().equals(ProcessViewProperty.DURATION);
  }

  private boolean isUserTaskCommand(ProcessViewDto viewDto) {
    return nonNull(viewDto) && nonNull(viewDto.getEntity()) &&
      viewDto.getEntity().equals(ProcessViewEntity.USER_TASK);
  }

  private boolean isGroupByAssigneeOrCandidateGroup(ProcessGroupByDto groupByDto) {
    return nonNull(groupByDto) && (
      ProcessGroupByType.ASSIGNEE.equals(groupByDto.getType()) ||
        ProcessGroupByType.CANDIDATE_GROUP.equals(groupByDto.getType())
    );
  }
}
