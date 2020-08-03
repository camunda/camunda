/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomNumberBucketDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.heatmap_target_value.HeatmapTargetValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.process_part.ProcessPartDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.SingleReportTargetValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.nonNull;

@AllArgsConstructor
@Builder
@Data
@FieldNameConstants(asEnum = true)
@NoArgsConstructor
public class SingleReportConfigurationDto implements Combinable {
  @Builder.Default
  private String color = ReportConstants.DEFAULT_CONFIGURATION_COLOR;
  @Builder.Default
  private AggregationType aggregationType = AggregationType.AVERAGE;
  @Builder.Default
  private FlowNodeExecutionState flowNodeExecutionState = FlowNodeExecutionState.ALL;
  @Builder.Default
  private UserTaskDurationTime userTaskDurationTime = UserTaskDurationTime.TOTAL;
  @Builder.Default
  private HiddenNodesDto hiddenNodes = new HiddenNodesDto();
  @Builder.Default
  private Boolean showInstanceCount = false;
  @Builder.Default
  private Boolean pointMarkers = true;
  @Builder.Default
  private Integer precision = null;
  @Builder.Default
  private Boolean hideRelativeValue = false;
  @Builder.Default
  private Boolean hideAbsoluteValue = false;
  @Builder.Default
  // needed to ensure the name is serialized properly, see https://stackoverflow.com/a/30207335
  @JsonProperty("yLabel")
  private String yLabel = "";
  @Builder.Default
  // needed to ensure the name is serialized properly, see https://stackoverflow.com/a/30207335
  @JsonProperty("xLabel")
  private String xLabel = "";
  @Builder.Default
  private Boolean alwaysShowRelative = false;
  @Builder.Default
  private Boolean alwaysShowAbsolute = false;
  @Builder.Default
  private Boolean showGradientBars = true;
  @Builder.Default
  private String xml = null;
  @Builder.Default
  private List<String> excludedColumns = new ArrayList<>();
  @Builder.Default
  private List<String> includedColumns = new ArrayList<>();
  @Builder.Default
  private ColumnOrderDto columnOrder = new ColumnOrderDto();
  @Builder.Default
  private SingleReportTargetValueDto targetValue = new SingleReportTargetValueDto();
  @Builder.Default
  private HeatmapTargetValueDto heatmapTargetValue = new HeatmapTargetValueDto();
  @Builder.Default
  private DistributedBy distributedBy = DistributedBy.NONE;
  @Builder.Default
  @NonNull
  private GroupByDateUnit groupByDateVariableUnit = GroupByDateUnit.AUTOMATIC;
  @Builder.Default
  private CustomNumberBucketDto customNumberBucket = new CustomNumberBucketDto();
  @Builder.Default
  private SortingDto sorting = null;
  @Builder.Default
  private ProcessPartDto processPart = null;

  @JsonIgnore
  public String createCommandKey(ProcessViewDto viewDto) {
    final List<String> configsToConsiderForCommand = new ArrayList<>();
    if (isUserTaskCommand(viewDto)) {
      configsToConsiderForCommand.add(this.distributedBy.getId());
    }
    getProcessPart().ifPresent(processPartDto -> configsToConsiderForCommand.add(processPartDto.createCommandKey()));
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
    return DistributedBy.NONE.equals(distributedBy) && DistributedBy.NONE.equals(that.distributedBy);
  }

  private boolean isUserTaskCommand(ProcessViewDto viewDto) {
    return nonNull(viewDto) && nonNull(viewDto.getEntity()) &&
      viewDto.getEntity().equals(ProcessViewEntity.USER_TASK);
  }

  public Optional<SortingDto> getSorting() {
    return Optional.ofNullable(sorting);
  }

  public Optional<ProcessPartDto> getProcessPart() {
    return Optional.ofNullable(processPart);
  }

  public Optional<Double> getBaselineForNumberVariableReport() {
    return customNumberBucket.isActive()
      ? Optional.ofNullable(customNumberBucket.getBaseline())
      : Optional.empty();
  }

  public Optional<Double> getBucketSizeForNumberVariableReport() {
    return customNumberBucket.isActive()
      ? Optional.ofNullable(customNumberBucket.getBucketSize())
      : Optional.empty();
  }
}
