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
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.heatmap_target_value.HeatmapTargetValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.process_part.ProcessPartDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.SingleReportTargetValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.nonNull;

@Data
@FieldNameConstants(asEnum = true)
public class SingleReportConfigurationDto implements Combinable {
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
  private SortingDto sorting = null;
  private ProcessPartDto processPart = null;

  @JsonIgnore
  public String createCommandKey(ProcessViewDto viewDto, ProcessGroupByDto groupByDto) {
    final List<String> configsToConsiderForCommand = new ArrayList<>();
    if (isUserTaskCommand(viewDto) && isGroupByAssigneeOrCandidateGroupOrFlowNode(groupByDto)) {
      configsToConsiderForCommand.add(this.distributedBy.getId());
    }
    if (getProcessPart().isPresent()) {
      configsToConsiderForCommand.add(getProcessPart().get().createCommandKey());
    }
    return String.join("-", configsToConsiderForCommand);
  }

  @Override
  public boolean isCombinable(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SingleReportConfigurationDto)) {
      return false;
    }
    SingleReportConfigurationDto that = (SingleReportConfigurationDto) o;
    return (distributedBy != DistributedBy.USER_TASK && that.distributedBy != DistributedBy.USER_TASK);
  }

  private boolean isUserTaskCommand(ProcessViewDto viewDto) {
    return nonNull(viewDto) && nonNull(viewDto.getEntity()) &&
      viewDto.getEntity().equals(ProcessViewEntity.USER_TASK);
  }

  private boolean isGroupByAssigneeOrCandidateGroupOrFlowNode(ProcessGroupByDto groupByDto) {
    return nonNull(groupByDto) && (
      ProcessGroupByType.ASSIGNEE.equals(groupByDto.getType()) ||
        ProcessGroupByType.CANDIDATE_GROUP.equals(groupByDto.getType()) ||
        ProcessGroupByType.FLOW_NODES.equals(groupByDto.getType())
    );
  }

  public Optional<SortingDto> getSorting() {
    return Optional.ofNullable(sorting);
  }

  public Optional<ProcessPartDto> getProcessPart() {
    return Optional.ofNullable(processPart);
  }
}
