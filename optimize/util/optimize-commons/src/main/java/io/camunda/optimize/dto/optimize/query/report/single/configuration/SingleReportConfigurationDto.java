/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.dto.optimize.ReportConstants;
import io.camunda.optimize.dto.optimize.query.report.Combinable;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.heatmap_target_value.HeatmapTargetValueDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.process_part.ProcessPartDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.SingleReportTargetValueDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor
@Builder
@Data
@NoArgsConstructor
public class SingleReportConfigurationDto implements Combinable {

  @Builder.Default private String color = ReportConstants.DEFAULT_CONFIGURATION_COLOR;

  @Builder.Default
  private Set<AggregationDto> aggregationTypes =
      new LinkedHashSet<>(Collections.singletonList(new AggregationDto(AggregationType.AVERAGE)));

  @Builder.Default
  private Set<UserTaskDurationTime> userTaskDurationTimes =
      new LinkedHashSet<>(Collections.singletonList(UserTaskDurationTime.TOTAL));

  @Builder.Default private Boolean showInstanceCount = false;
  @Builder.Default private Boolean pointMarkers = true;
  @Builder.Default private Integer precision = null;
  @Builder.Default private Boolean hideRelativeValue = false;
  @Builder.Default private Boolean hideAbsoluteValue = false;

  @Builder.Default
  // needed to ensure the name is serialized properly, see https://stackoverflow.com/a/30207335
  @JsonProperty("yLabel")
  private String yLabel = "";

  @Builder.Default
  // needed to ensure the name is serialized properly, see https://stackoverflow.com/a/30207335
  @JsonProperty("xLabel")
  private String xLabel = "";

  @Builder.Default private Boolean alwaysShowRelative = false;
  @Builder.Default private Boolean alwaysShowAbsolute = false;
  @Builder.Default private Boolean showGradientBars = true;
  @Builder.Default private String xml = null;
  @Builder.Default private TableColumnDto tableColumns = new TableColumnDto();

  @Builder.Default
  private SingleReportTargetValueDto targetValue = new SingleReportTargetValueDto();

  @Builder.Default private HeatmapTargetValueDto heatmapTargetValue = new HeatmapTargetValueDto();

  @Builder.Default @NonNull
  private AggregateByDateUnit groupByDateVariableUnit = AggregateByDateUnit.AUTOMATIC;

  @Builder.Default @NonNull
  private AggregateByDateUnit distributeByDateVariableUnit = AggregateByDateUnit.AUTOMATIC;

  @Builder.Default private CustomBucketDto customBucket = CustomBucketDto.builder().build();

  @Builder.Default
  private CustomBucketDto distributeByCustomBucket = CustomBucketDto.builder().build();

  @Builder.Default private ReportSortingDto sorting = null;
  @Builder.Default private ProcessPartDto processPart = null;

  @Builder.Default
  private MeasureVisualizationsDto measureVisualizations = new MeasureVisualizationsDto();

  @Builder.Default private Boolean stackedBar = false;
  @Builder.Default private Boolean horizontalBar = false;
  @Builder.Default private Boolean logScale = false;

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
    return aggregationTypesAmount <= 1
        && aggregationTypesAmount == that.getAggregationTypes().size()
        && userTaskDurationTimesAmount <= 1
        && userTaskDurationTimesAmount == that.getUserTaskDurationTimes().size();
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

  public static final class Fields {

    public static final String color = "color";
    public static final String aggregationTypes = "aggregationTypes";
    public static final String userTaskDurationTimes = "userTaskDurationTimes";
    public static final String showInstanceCount = "showInstanceCount";
    public static final String pointMarkers = "pointMarkers";
    public static final String precision = "precision";
    public static final String hideRelativeValue = "hideRelativeValue";
    public static final String hideAbsoluteValue = "hideAbsoluteValue";
    public static final String yLabel = "yLabel";
    public static final String xLabel = "xLabel";
    public static final String alwaysShowRelative = "alwaysShowRelative";
    public static final String alwaysShowAbsolute = "alwaysShowAbsolute";
    public static final String showGradientBars = "showGradientBars";
    public static final String xml = "xml";
    public static final String tableColumns = "tableColumns";
    public static final String targetValue = "targetValue";
    public static final String heatmapTargetValue = "heatmapTargetValue";
    public static final String groupByDateVariableUnit = "groupByDateVariableUnit";
    public static final String distributeByDateVariableUnit = "distributeByDateVariableUnit";
    public static final String customBucket = "customBucket";
    public static final String distributeByCustomBucket = "distributeByCustomBucket";
    public static final String sorting = "sorting";
    public static final String processPart = "processPart";
    public static final String measureVisualizations = "measureVisualizations";
    public static final String stackedBar = "stackedBar";
    public static final String horizontalBar = "horizontalBar";
    public static final String logScale = "logScale";
  }
}
