/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
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
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.heatmap_target_value.HeatmapTargetValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.process_part.ProcessPartDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.SingleReportTargetValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@AllArgsConstructor
@Builder
@Data
@FieldNameConstants
@NoArgsConstructor
public class SingleReportConfigurationDto implements Combinable {
  @Builder.Default
  private String color = ReportConstants.DEFAULT_CONFIGURATION_COLOR;
  @Builder.Default
  private Set<AggregationDto> aggregationTypes =
    new LinkedHashSet<>(Collections.singletonList(new AggregationDto(AggregationType.AVERAGE)));
  @Builder.Default
  private Set<UserTaskDurationTime> userTaskDurationTimes =
    new LinkedHashSet<>(Collections.singletonList(UserTaskDurationTime.TOTAL));
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
  private TableColumnDto tableColumns = new TableColumnDto();
  @Builder.Default
  private SingleReportTargetValueDto targetValue = new SingleReportTargetValueDto();
  @Builder.Default
  private HeatmapTargetValueDto heatmapTargetValue = new HeatmapTargetValueDto();
  @Builder.Default
  @NonNull
  private AggregateByDateUnit groupByDateVariableUnit = AggregateByDateUnit.AUTOMATIC;
  @Builder.Default
  @NonNull
  private AggregateByDateUnit distributeByDateVariableUnit = AggregateByDateUnit.AUTOMATIC;
  @Builder.Default
  private CustomBucketDto customBucket = CustomBucketDto.builder().build();
  @Builder.Default
  private CustomBucketDto distributeByCustomBucket = CustomBucketDto.builder().build();
  @Builder.Default
  private ReportSortingDto sorting = null;
  @Builder.Default
  private ProcessPartDto processPart = null;
  @Builder.Default
  private MeasureVisualizationsDto measureVisualizations = new MeasureVisualizationsDto();
  @Builder.Default
  private Boolean stackedBar = false;
  @Builder.Default
  private Boolean logScale = false;

  @JsonIgnore
  public String createCommandKey() {
    return getProcessPart().map(ProcessPartDto::createCommandKey).orElse(null);
  }

  @Override
  public boolean isCombinable(final Object o) {
    if (!(o instanceof SingleReportConfigurationDto)) {
      return false;
    }
    final SingleReportConfigurationDto that = (SingleReportConfigurationDto) o;
    final int aggregationTypesAmount = getAggregationTypes().size();
    final int userTaskDurationTimesAmount = getUserTaskDurationTimes().size();
    return aggregationTypesAmount <= 1 && aggregationTypesAmount == that.getAggregationTypes().size()
      && userTaskDurationTimesAmount <= 1 && userTaskDurationTimesAmount == that.getUserTaskDurationTimes().size();
  }

  public void setAggregationTypes(final AggregationDto... aggregationTypes) {
    // deduplication using an intermediate set
    this.aggregationTypes = new LinkedHashSet<>(Arrays.asList(aggregationTypes));
  }

  public void setUserTaskDurationTimes(final UserTaskDurationTime... userTaskDurationTimes) {
    // deduplication using an intermediate set
    this.userTaskDurationTimes = new LinkedHashSet<>(Arrays.asList(userTaskDurationTimes));
  }

  public Optional<ReportSortingDto> getSorting() {
    return Optional.ofNullable(sorting);
  }

  public Optional<ProcessPartDto> getProcessPart() {
    return Optional.ofNullable(processPart);
  }
}
