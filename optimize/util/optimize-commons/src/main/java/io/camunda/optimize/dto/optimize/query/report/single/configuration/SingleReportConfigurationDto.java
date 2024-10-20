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

public class SingleReportConfigurationDto implements Combinable {

  private String color = ReportConstants.DEFAULT_CONFIGURATION_COLOR;

  private Set<AggregationDto> aggregationTypes =
      new LinkedHashSet<>(Collections.singletonList(new AggregationDto(AggregationType.AVERAGE)));

  private Set<UserTaskDurationTime> userTaskDurationTimes =
      new LinkedHashSet<>(Collections.singletonList(UserTaskDurationTime.TOTAL));

  private Boolean showInstanceCount = false;
  private Boolean pointMarkers = true;
  private Integer precision = null;
  private Boolean hideRelativeValue = false;
  private Boolean hideAbsoluteValue = false;

  // needed to ensure the name is serialized properly, see https://stackoverflow.com/a/30207335
  @JsonProperty("yLabel")
  private String yLabel = "";

  // needed to ensure the name is serialized properly, see https://stackoverflow.com/a/30207335
  @JsonProperty("xLabel")
  private String xLabel = "";

  private Boolean alwaysShowRelative = false;
  private Boolean alwaysShowAbsolute = false;
  private Boolean showGradientBars = true;
  private String xml = null;
  private TableColumnDto tableColumns = new TableColumnDto();
  private SingleReportTargetValueDto targetValue = new SingleReportTargetValueDto();
  private HeatmapTargetValueDto heatmapTargetValue = new HeatmapTargetValueDto();
  private AggregateByDateUnit groupByDateVariableUnit = AggregateByDateUnit.AUTOMATIC;
  private AggregateByDateUnit distributeByDateVariableUnit = AggregateByDateUnit.AUTOMATIC;
  private CustomBucketDto customBucket = CustomBucketDto.builder().build();
  private CustomBucketDto distributeByCustomBucket = CustomBucketDto.builder().build();
  private ReportSortingDto sorting = null;
  private ProcessPartDto processPart = null;
  private MeasureVisualizationsDto measureVisualizations = new MeasureVisualizationsDto();
  private Boolean stackedBar = false;
  private Boolean horizontalBar = false;
  private Boolean logScale = false;

  public SingleReportConfigurationDto(
      final String color,
      final Set<AggregationDto> aggregationTypes,
      final Set<UserTaskDurationTime> userTaskDurationTimes,
      final Boolean showInstanceCount,
      final Boolean pointMarkers,
      final Integer precision,
      final Boolean hideRelativeValue,
      final Boolean hideAbsoluteValue,
      final String yLabel,
      final String xLabel,
      final Boolean alwaysShowRelative,
      final Boolean alwaysShowAbsolute,
      final Boolean showGradientBars,
      final String xml,
      final TableColumnDto tableColumns,
      final SingleReportTargetValueDto targetValue,
      final HeatmapTargetValueDto heatmapTargetValue,
      final AggregateByDateUnit groupByDateVariableUnit,
      final AggregateByDateUnit distributeByDateVariableUnit,
      final CustomBucketDto customBucket,
      final CustomBucketDto distributeByCustomBucket,
      final ReportSortingDto sorting,
      final ProcessPartDto processPart,
      final MeasureVisualizationsDto measureVisualizations,
      final Boolean stackedBar,
      final Boolean horizontalBar,
      final Boolean logScale) {
    if (groupByDateVariableUnit == null) {
      throw new IllegalArgumentException("groupByDateVariableUnit must not be null");
    }

    if (distributeByDateVariableUnit == null) {
      throw new IllegalArgumentException("distributeByDateVariableUnit must not be null");
    }

    this.color = color;
    this.aggregationTypes = aggregationTypes;
    this.userTaskDurationTimes = userTaskDurationTimes;
    this.showInstanceCount = showInstanceCount;
    this.pointMarkers = pointMarkers;
    this.precision = precision;
    this.hideRelativeValue = hideRelativeValue;
    this.hideAbsoluteValue = hideAbsoluteValue;
    this.yLabel = yLabel;
    this.xLabel = xLabel;
    this.alwaysShowRelative = alwaysShowRelative;
    this.alwaysShowAbsolute = alwaysShowAbsolute;
    this.showGradientBars = showGradientBars;
    this.xml = xml;
    this.tableColumns = tableColumns;
    this.targetValue = targetValue;
    this.heatmapTargetValue = heatmapTargetValue;
    this.groupByDateVariableUnit = groupByDateVariableUnit;
    this.distributeByDateVariableUnit = distributeByDateVariableUnit;
    this.customBucket = customBucket;
    this.distributeByCustomBucket = distributeByCustomBucket;
    this.sorting = sorting;
    this.processPart = processPart;
    this.measureVisualizations = measureVisualizations;
    this.stackedBar = stackedBar;
    this.horizontalBar = horizontalBar;
    this.logScale = logScale;
  }

  public SingleReportConfigurationDto() {}

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

  public Optional<ReportSortingDto> getSorting() {
    return Optional.ofNullable(sorting);
  }

  public void setSorting(final ReportSortingDto sorting) {
    this.sorting = sorting;
  }

  public Optional<ProcessPartDto> getProcessPart() {
    return Optional.ofNullable(processPart);
  }

  public void setProcessPart(final ProcessPartDto processPart) {
    this.processPart = processPart;
  }

  public String getColor() {
    return color;
  }

  public void setColor(final String color) {
    this.color = color;
  }

  public Set<AggregationDto> getAggregationTypes() {
    return aggregationTypes;
  }

  public void setAggregationTypes(final AggregationDto... aggregationTypes) {
    // deduplication using an intermediate set
    this.aggregationTypes = new LinkedHashSet<>(Arrays.asList(aggregationTypes));
  }

  public Set<UserTaskDurationTime> getUserTaskDurationTimes() {
    return userTaskDurationTimes;
  }

  public void setUserTaskDurationTimes(final UserTaskDurationTime... userTaskDurationTimes) {
    // deduplication using an intermediate set
    this.userTaskDurationTimes = new LinkedHashSet<>(Arrays.asList(userTaskDurationTimes));
  }

  public Boolean getShowInstanceCount() {
    return showInstanceCount;
  }

  public void setShowInstanceCount(final Boolean showInstanceCount) {
    this.showInstanceCount = showInstanceCount;
  }

  public Boolean getPointMarkers() {
    return pointMarkers;
  }

  public void setPointMarkers(final Boolean pointMarkers) {
    this.pointMarkers = pointMarkers;
  }

  public Integer getPrecision() {
    return precision;
  }

  public void setPrecision(final Integer precision) {
    this.precision = precision;
  }

  public Boolean getHideRelativeValue() {
    return hideRelativeValue;
  }

  public void setHideRelativeValue(final Boolean hideRelativeValue) {
    this.hideRelativeValue = hideRelativeValue;
  }

  public Boolean getHideAbsoluteValue() {
    return hideAbsoluteValue;
  }

  public void setHideAbsoluteValue(final Boolean hideAbsoluteValue) {
    this.hideAbsoluteValue = hideAbsoluteValue;
  }

  public String getYLabel() {
    return yLabel;
  }

  @JsonProperty("yLabel")
  public void setYLabel(final String yLabel) {
    this.yLabel = yLabel;
  }

  public String getXLabel() {
    return xLabel;
  }

  @JsonProperty("xLabel")
  public void setXLabel(final String xLabel) {
    this.xLabel = xLabel;
  }

  public Boolean getAlwaysShowRelative() {
    return alwaysShowRelative;
  }

  public void setAlwaysShowRelative(final Boolean alwaysShowRelative) {
    this.alwaysShowRelative = alwaysShowRelative;
  }

  public Boolean getAlwaysShowAbsolute() {
    return alwaysShowAbsolute;
  }

  public void setAlwaysShowAbsolute(final Boolean alwaysShowAbsolute) {
    this.alwaysShowAbsolute = alwaysShowAbsolute;
  }

  public Boolean getShowGradientBars() {
    return showGradientBars;
  }

  public void setShowGradientBars(final Boolean showGradientBars) {
    this.showGradientBars = showGradientBars;
  }

  public String getXml() {
    return xml;
  }

  public void setXml(final String xml) {
    this.xml = xml;
  }

  public TableColumnDto getTableColumns() {
    return tableColumns;
  }

  public void setTableColumns(final TableColumnDto tableColumns) {
    this.tableColumns = tableColumns;
  }

  public SingleReportTargetValueDto getTargetValue() {
    return targetValue;
  }

  public void setTargetValue(final SingleReportTargetValueDto targetValue) {
    this.targetValue = targetValue;
  }

  public HeatmapTargetValueDto getHeatmapTargetValue() {
    return heatmapTargetValue;
  }

  public void setHeatmapTargetValue(final HeatmapTargetValueDto heatmapTargetValue) {
    this.heatmapTargetValue = heatmapTargetValue;
  }

  public AggregateByDateUnit getGroupByDateVariableUnit() {
    return groupByDateVariableUnit;
  }

  public void setGroupByDateVariableUnit(final AggregateByDateUnit groupByDateVariableUnit) {
    if (groupByDateVariableUnit == null) {
      throw new IllegalArgumentException("groupByDateVariableUnit must not be null");
    }

    this.groupByDateVariableUnit = groupByDateVariableUnit;
  }

  public AggregateByDateUnit getDistributeByDateVariableUnit() {
    return distributeByDateVariableUnit;
  }

  public void setDistributeByDateVariableUnit(
      final AggregateByDateUnit distributeByDateVariableUnit) {
    if (distributeByDateVariableUnit == null) {
      throw new IllegalArgumentException("distributeByDateVariableUnit must not be null");
    }

    this.distributeByDateVariableUnit = distributeByDateVariableUnit;
  }

  public CustomBucketDto getCustomBucket() {
    return customBucket;
  }

  public void setCustomBucket(final CustomBucketDto customBucket) {
    this.customBucket = customBucket;
  }

  public CustomBucketDto getDistributeByCustomBucket() {
    return distributeByCustomBucket;
  }

  public void setDistributeByCustomBucket(final CustomBucketDto distributeByCustomBucket) {
    this.distributeByCustomBucket = distributeByCustomBucket;
  }

  public MeasureVisualizationsDto getMeasureVisualizations() {
    return measureVisualizations;
  }

  public void setMeasureVisualizations(final MeasureVisualizationsDto measureVisualizations) {
    this.measureVisualizations = measureVisualizations;
  }

  public Boolean getStackedBar() {
    return stackedBar;
  }

  public void setStackedBar(final Boolean stackedBar) {
    this.stackedBar = stackedBar;
  }

  public Boolean getHorizontalBar() {
    return horizontalBar;
  }

  public void setHorizontalBar(final Boolean horizontalBar) {
    this.horizontalBar = horizontalBar;
  }

  public Boolean getLogScale() {
    return logScale;
  }

  public void setLogScale(final Boolean logScale) {
    this.logScale = logScale;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof SingleReportConfigurationDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "SingleReportConfigurationDto(color="
        + getColor()
        + ", aggregationTypes="
        + getAggregationTypes()
        + ", userTaskDurationTimes="
        + getUserTaskDurationTimes()
        + ", showInstanceCount="
        + getShowInstanceCount()
        + ", pointMarkers="
        + getPointMarkers()
        + ", precision="
        + getPrecision()
        + ", hideRelativeValue="
        + getHideRelativeValue()
        + ", hideAbsoluteValue="
        + getHideAbsoluteValue()
        + ", yLabel="
        + getYLabel()
        + ", xLabel="
        + getXLabel()
        + ", alwaysShowRelative="
        + getAlwaysShowRelative()
        + ", alwaysShowAbsolute="
        + getAlwaysShowAbsolute()
        + ", showGradientBars="
        + getShowGradientBars()
        + ", xml="
        + getXml()
        + ", tableColumns="
        + getTableColumns()
        + ", targetValue="
        + getTargetValue()
        + ", heatmapTargetValue="
        + getHeatmapTargetValue()
        + ", groupByDateVariableUnit="
        + getGroupByDateVariableUnit()
        + ", distributeByDateVariableUnit="
        + getDistributeByDateVariableUnit()
        + ", customBucket="
        + getCustomBucket()
        + ", distributeByCustomBucket="
        + getDistributeByCustomBucket()
        + ", sorting="
        + getSorting()
        + ", processPart="
        + getProcessPart()
        + ", measureVisualizations="
        + getMeasureVisualizations()
        + ", stackedBar="
        + getStackedBar()
        + ", horizontalBar="
        + getHorizontalBar()
        + ", logScale="
        + getLogScale()
        + ")";
  }

  private static String $default$color() {
    return ReportConstants.DEFAULT_CONFIGURATION_COLOR;
  }

  private static Set<AggregationDto> $default$aggregationTypes() {
    return new LinkedHashSet<>(
        Collections.singletonList(new AggregationDto(AggregationType.AVERAGE)));
  }

  private static Set<UserTaskDurationTime> $default$userTaskDurationTimes() {
    return new LinkedHashSet<>(Collections.singletonList(UserTaskDurationTime.TOTAL));
  }

  private static Boolean $default$showInstanceCount() {
    return false;
  }

  private static Boolean $default$pointMarkers() {
    return true;
  }

  private static Integer $default$precision() {
    return null;
  }

  private static Boolean $default$hideRelativeValue() {
    return false;
  }

  private static Boolean $default$hideAbsoluteValue() {
    return false;
  }

  private static String $default$yLabel() {
    return "";
  }

  private static String $default$xLabel() {
    return "";
  }

  private static Boolean $default$alwaysShowRelative() {
    return false;
  }

  private static Boolean $default$alwaysShowAbsolute() {
    return false;
  }

  private static Boolean $default$showGradientBars() {
    return true;
  }

  private static String $default$xml() {
    return null;
  }

  private static TableColumnDto $default$tableColumns() {
    return new TableColumnDto();
  }

  private static SingleReportTargetValueDto $default$targetValue() {
    return new SingleReportTargetValueDto();
  }

  private static HeatmapTargetValueDto $default$heatmapTargetValue() {
    return new HeatmapTargetValueDto();
  }

  private static AggregateByDateUnit $default$groupByDateVariableUnit() {
    return AggregateByDateUnit.AUTOMATIC;
  }

  private static AggregateByDateUnit $default$distributeByDateVariableUnit() {
    return AggregateByDateUnit.AUTOMATIC;
  }

  private static CustomBucketDto $default$customBucket() {
    return CustomBucketDto.builder().build();
  }

  private static CustomBucketDto $default$distributeByCustomBucket() {
    return CustomBucketDto.builder().build();
  }

  private static ReportSortingDto $default$sorting() {
    return null;
  }

  private static ProcessPartDto $default$processPart() {
    return null;
  }

  private static MeasureVisualizationsDto $default$measureVisualizations() {
    return new MeasureVisualizationsDto();
  }

  private static Boolean $default$stackedBar() {
    return false;
  }

  private static Boolean $default$horizontalBar() {
    return false;
  }

  private static Boolean $default$logScale() {
    return false;
  }

  public static SingleReportConfigurationDtoBuilder builder() {
    return new SingleReportConfigurationDtoBuilder();
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

  public static class SingleReportConfigurationDtoBuilder {

    private String color$value;
    private boolean color$set;
    private Set<AggregationDto> aggregationTypes$value;
    private boolean aggregationTypes$set;
    private Set<UserTaskDurationTime> userTaskDurationTimes$value;
    private boolean userTaskDurationTimes$set;
    private Boolean showInstanceCount$value;
    private boolean showInstanceCount$set;
    private Boolean pointMarkers$value;
    private boolean pointMarkers$set;
    private Integer precision$value;
    private boolean precision$set;
    private Boolean hideRelativeValue$value;
    private boolean hideRelativeValue$set;
    private Boolean hideAbsoluteValue$value;
    private boolean hideAbsoluteValue$set;
    private String yLabel$value;
    private boolean yLabel$set;
    private String xLabel$value;
    private boolean xLabel$set;
    private Boolean alwaysShowRelative$value;
    private boolean alwaysShowRelative$set;
    private Boolean alwaysShowAbsolute$value;
    private boolean alwaysShowAbsolute$set;
    private Boolean showGradientBars$value;
    private boolean showGradientBars$set;
    private String xml$value;
    private boolean xml$set;
    private TableColumnDto tableColumns$value;
    private boolean tableColumns$set;
    private SingleReportTargetValueDto targetValue$value;
    private boolean targetValue$set;
    private HeatmapTargetValueDto heatmapTargetValue$value;
    private boolean heatmapTargetValue$set;
    private AggregateByDateUnit groupByDateVariableUnit$value;
    private boolean groupByDateVariableUnit$set;
    private AggregateByDateUnit distributeByDateVariableUnit$value;
    private boolean distributeByDateVariableUnit$set;
    private CustomBucketDto customBucket$value;
    private boolean customBucket$set;
    private CustomBucketDto distributeByCustomBucket$value;
    private boolean distributeByCustomBucket$set;
    private ReportSortingDto sorting$value;
    private boolean sorting$set;
    private ProcessPartDto processPart$value;
    private boolean processPart$set;
    private MeasureVisualizationsDto measureVisualizations$value;
    private boolean measureVisualizations$set;
    private Boolean stackedBar$value;
    private boolean stackedBar$set;
    private Boolean horizontalBar$value;
    private boolean horizontalBar$set;
    private Boolean logScale$value;
    private boolean logScale$set;

    SingleReportConfigurationDtoBuilder() {}

    public SingleReportConfigurationDtoBuilder color(final String color) {
      color$value = color;
      color$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder aggregationTypes(
        final Set<AggregationDto> aggregationTypes) {
      aggregationTypes$value = aggregationTypes;
      aggregationTypes$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder userTaskDurationTimes(
        final Set<UserTaskDurationTime> userTaskDurationTimes) {
      userTaskDurationTimes$value = userTaskDurationTimes;
      userTaskDurationTimes$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder showInstanceCount(final Boolean showInstanceCount) {
      showInstanceCount$value = showInstanceCount;
      showInstanceCount$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder pointMarkers(final Boolean pointMarkers) {
      pointMarkers$value = pointMarkers;
      pointMarkers$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder precision(final Integer precision) {
      precision$value = precision;
      precision$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder hideRelativeValue(final Boolean hideRelativeValue) {
      hideRelativeValue$value = hideRelativeValue;
      hideRelativeValue$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder hideAbsoluteValue(final Boolean hideAbsoluteValue) {
      hideAbsoluteValue$value = hideAbsoluteValue;
      hideAbsoluteValue$set = true;
      return this;
    }

    @JsonProperty("yLabel")
    public SingleReportConfigurationDtoBuilder yLabel(final String yLabel) {
      yLabel$value = yLabel;
      yLabel$set = true;
      return this;
    }

    @JsonProperty("xLabel")
    public SingleReportConfigurationDtoBuilder xLabel(final String xLabel) {
      xLabel$value = xLabel;
      xLabel$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder alwaysShowRelative(
        final Boolean alwaysShowRelative) {
      alwaysShowRelative$value = alwaysShowRelative;
      alwaysShowRelative$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder alwaysShowAbsolute(
        final Boolean alwaysShowAbsolute) {
      alwaysShowAbsolute$value = alwaysShowAbsolute;
      alwaysShowAbsolute$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder showGradientBars(final Boolean showGradientBars) {
      showGradientBars$value = showGradientBars;
      showGradientBars$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder xml(final String xml) {
      xml$value = xml;
      xml$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder tableColumns(final TableColumnDto tableColumns) {
      tableColumns$value = tableColumns;
      tableColumns$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder targetValue(
        final SingleReportTargetValueDto targetValue) {
      targetValue$value = targetValue;
      targetValue$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder heatmapTargetValue(
        final HeatmapTargetValueDto heatmapTargetValue) {
      heatmapTargetValue$value = heatmapTargetValue;
      heatmapTargetValue$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder groupByDateVariableUnit(
        final AggregateByDateUnit groupByDateVariableUnit) {
      if (groupByDateVariableUnit == null) {
        throw new IllegalArgumentException("groupByDateVariableUnit must not be null");
      }

      groupByDateVariableUnit$value = groupByDateVariableUnit;
      groupByDateVariableUnit$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder distributeByDateVariableUnit(
        final AggregateByDateUnit distributeByDateVariableUnit) {
      if (distributeByDateVariableUnit == null) {
        throw new IllegalArgumentException("distributeByDateVariableUnit must not be null");
      }

      distributeByDateVariableUnit$value = distributeByDateVariableUnit;
      distributeByDateVariableUnit$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder customBucket(final CustomBucketDto customBucket) {
      customBucket$value = customBucket;
      customBucket$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder distributeByCustomBucket(
        final CustomBucketDto distributeByCustomBucket) {
      distributeByCustomBucket$value = distributeByCustomBucket;
      distributeByCustomBucket$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder sorting(final ReportSortingDto sorting) {
      sorting$value = sorting;
      sorting$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder processPart(final ProcessPartDto processPart) {
      processPart$value = processPart;
      processPart$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder measureVisualizations(
        final MeasureVisualizationsDto measureVisualizations) {
      measureVisualizations$value = measureVisualizations;
      measureVisualizations$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder stackedBar(final Boolean stackedBar) {
      stackedBar$value = stackedBar;
      stackedBar$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder horizontalBar(final Boolean horizontalBar) {
      horizontalBar$value = horizontalBar;
      horizontalBar$set = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder logScale(final Boolean logScale) {
      logScale$value = logScale;
      logScale$set = true;
      return this;
    }

    public SingleReportConfigurationDto build() {
      String color$value = this.color$value;
      if (!color$set) {
        color$value = SingleReportConfigurationDto.$default$color();
      }
      Set<AggregationDto> aggregationTypes$value = this.aggregationTypes$value;
      if (!aggregationTypes$set) {
        aggregationTypes$value = SingleReportConfigurationDto.$default$aggregationTypes();
      }
      Set<UserTaskDurationTime> userTaskDurationTimes$value = this.userTaskDurationTimes$value;
      if (!userTaskDurationTimes$set) {
        userTaskDurationTimes$value = SingleReportConfigurationDto.$default$userTaskDurationTimes();
      }
      Boolean showInstanceCount$value = this.showInstanceCount$value;
      if (!showInstanceCount$set) {
        showInstanceCount$value = SingleReportConfigurationDto.$default$showInstanceCount();
      }
      Boolean pointMarkers$value = this.pointMarkers$value;
      if (!pointMarkers$set) {
        pointMarkers$value = SingleReportConfigurationDto.$default$pointMarkers();
      }
      Integer precision$value = this.precision$value;
      if (!precision$set) {
        precision$value = SingleReportConfigurationDto.$default$precision();
      }
      Boolean hideRelativeValue$value = this.hideRelativeValue$value;
      if (!hideRelativeValue$set) {
        hideRelativeValue$value = SingleReportConfigurationDto.$default$hideRelativeValue();
      }
      Boolean hideAbsoluteValue$value = this.hideAbsoluteValue$value;
      if (!hideAbsoluteValue$set) {
        hideAbsoluteValue$value = SingleReportConfigurationDto.$default$hideAbsoluteValue();
      }
      String yLabel$value = this.yLabel$value;
      if (!yLabel$set) {
        yLabel$value = SingleReportConfigurationDto.$default$yLabel();
      }
      String xLabel$value = this.xLabel$value;
      if (!xLabel$set) {
        xLabel$value = SingleReportConfigurationDto.$default$xLabel();
      }
      Boolean alwaysShowRelative$value = this.alwaysShowRelative$value;
      if (!alwaysShowRelative$set) {
        alwaysShowRelative$value = SingleReportConfigurationDto.$default$alwaysShowRelative();
      }
      Boolean alwaysShowAbsolute$value = this.alwaysShowAbsolute$value;
      if (!alwaysShowAbsolute$set) {
        alwaysShowAbsolute$value = SingleReportConfigurationDto.$default$alwaysShowAbsolute();
      }
      Boolean showGradientBars$value = this.showGradientBars$value;
      if (!showGradientBars$set) {
        showGradientBars$value = SingleReportConfigurationDto.$default$showGradientBars();
      }
      String xml$value = this.xml$value;
      if (!xml$set) {
        xml$value = SingleReportConfigurationDto.$default$xml();
      }
      TableColumnDto tableColumns$value = this.tableColumns$value;
      if (!tableColumns$set) {
        tableColumns$value = SingleReportConfigurationDto.$default$tableColumns();
      }
      SingleReportTargetValueDto targetValue$value = this.targetValue$value;
      if (!targetValue$set) {
        targetValue$value = SingleReportConfigurationDto.$default$targetValue();
      }
      HeatmapTargetValueDto heatmapTargetValue$value = this.heatmapTargetValue$value;
      if (!heatmapTargetValue$set) {
        heatmapTargetValue$value = SingleReportConfigurationDto.$default$heatmapTargetValue();
      }
      AggregateByDateUnit groupByDateVariableUnit$value = this.groupByDateVariableUnit$value;
      if (!groupByDateVariableUnit$set) {
        groupByDateVariableUnit$value =
            SingleReportConfigurationDto.$default$groupByDateVariableUnit();
      }
      AggregateByDateUnit distributeByDateVariableUnit$value =
          this.distributeByDateVariableUnit$value;
      if (!distributeByDateVariableUnit$set) {
        distributeByDateVariableUnit$value =
            SingleReportConfigurationDto.$default$distributeByDateVariableUnit();
      }
      CustomBucketDto customBucket$value = this.customBucket$value;
      if (!customBucket$set) {
        customBucket$value = SingleReportConfigurationDto.$default$customBucket();
      }
      CustomBucketDto distributeByCustomBucket$value = this.distributeByCustomBucket$value;
      if (!distributeByCustomBucket$set) {
        distributeByCustomBucket$value =
            SingleReportConfigurationDto.$default$distributeByCustomBucket();
      }
      ReportSortingDto sorting$value = this.sorting$value;
      if (!sorting$set) {
        sorting$value = SingleReportConfigurationDto.$default$sorting();
      }
      ProcessPartDto processPart$value = this.processPart$value;
      if (!processPart$set) {
        processPart$value = SingleReportConfigurationDto.$default$processPart();
      }
      MeasureVisualizationsDto measureVisualizations$value = this.measureVisualizations$value;
      if (!measureVisualizations$set) {
        measureVisualizations$value = SingleReportConfigurationDto.$default$measureVisualizations();
      }
      Boolean stackedBar$value = this.stackedBar$value;
      if (!stackedBar$set) {
        stackedBar$value = SingleReportConfigurationDto.$default$stackedBar();
      }
      Boolean horizontalBar$value = this.horizontalBar$value;
      if (!horizontalBar$set) {
        horizontalBar$value = SingleReportConfigurationDto.$default$horizontalBar();
      }
      Boolean logScale$value = this.logScale$value;
      if (!logScale$set) {
        logScale$value = SingleReportConfigurationDto.$default$logScale();
      }
      return new SingleReportConfigurationDto(
          color$value,
          aggregationTypes$value,
          userTaskDurationTimes$value,
          showInstanceCount$value,
          pointMarkers$value,
          precision$value,
          hideRelativeValue$value,
          hideAbsoluteValue$value,
          yLabel$value,
          xLabel$value,
          alwaysShowRelative$value,
          alwaysShowAbsolute$value,
          showGradientBars$value,
          xml$value,
          tableColumns$value,
          targetValue$value,
          heatmapTargetValue$value,
          groupByDateVariableUnit$value,
          distributeByDateVariableUnit$value,
          customBucket$value,
          distributeByCustomBucket$value,
          sorting$value,
          processPart$value,
          measureVisualizations$value,
          stackedBar$value,
          horizontalBar$value,
          logScale$value);
    }

    @Override
    public String toString() {
      return "SingleReportConfigurationDto.SingleReportConfigurationDtoBuilder(color$value="
          + color$value
          + ", aggregationTypes$value="
          + aggregationTypes$value
          + ", userTaskDurationTimes$value="
          + userTaskDurationTimes$value
          + ", showInstanceCount$value="
          + showInstanceCount$value
          + ", pointMarkers$value="
          + pointMarkers$value
          + ", precision$value="
          + precision$value
          + ", hideRelativeValue$value="
          + hideRelativeValue$value
          + ", hideAbsoluteValue$value="
          + hideAbsoluteValue$value
          + ", yLabel$value="
          + yLabel$value
          + ", xLabel$value="
          + xLabel$value
          + ", alwaysShowRelative$value="
          + alwaysShowRelative$value
          + ", alwaysShowAbsolute$value="
          + alwaysShowAbsolute$value
          + ", showGradientBars$value="
          + showGradientBars$value
          + ", xml$value="
          + xml$value
          + ", tableColumns$value="
          + tableColumns$value
          + ", targetValue$value="
          + targetValue$value
          + ", heatmapTargetValue$value="
          + heatmapTargetValue$value
          + ", groupByDateVariableUnit$value="
          + groupByDateVariableUnit$value
          + ", distributeByDateVariableUnit$value="
          + distributeByDateVariableUnit$value
          + ", customBucket$value="
          + customBucket$value
          + ", distributeByCustomBucket$value="
          + distributeByCustomBucket$value
          + ", sorting$value="
          + sorting$value
          + ", processPart$value="
          + processPart$value
          + ", measureVisualizations$value="
          + measureVisualizations$value
          + ", stackedBar$value="
          + stackedBar$value
          + ", horizontalBar$value="
          + horizontalBar$value
          + ", logScale$value="
          + logScale$value
          + ")";
    }
  }
}
