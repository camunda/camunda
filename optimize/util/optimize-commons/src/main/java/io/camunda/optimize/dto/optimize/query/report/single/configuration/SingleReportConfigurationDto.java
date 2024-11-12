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

  private static String defaultColor() {
    return ReportConstants.DEFAULT_CONFIGURATION_COLOR;
  }

  private static Set<AggregationDto> defaultAggregationTypes() {
    return new LinkedHashSet<>(
        Collections.singletonList(new AggregationDto(AggregationType.AVERAGE)));
  }

  private static Set<UserTaskDurationTime> defaultUserTaskDurationTimes() {
    return new LinkedHashSet<>(Collections.singletonList(UserTaskDurationTime.TOTAL));
  }

  private static Boolean defaultShowInstanceCount() {
    return false;
  }

  private static Boolean defaultPointMarkers() {
    return true;
  }

  private static Integer defaultPrecision() {
    return null;
  }

  private static Boolean defaultHideRelativeValue() {
    return false;
  }

  private static Boolean defaultHideAbsoluteValue() {
    return false;
  }

  private static String defaultYLabel() {
    return "";
  }

  private static String defaultXLabel() {
    return "";
  }

  private static Boolean defaultAlwaysShowRelative() {
    return false;
  }

  private static Boolean defaultAlwaysShowAbsolute() {
    return false;
  }

  private static Boolean defaultShowGradientBars() {
    return true;
  }

  private static String defaultXml() {
    return null;
  }

  private static TableColumnDto defaultTableColumns() {
    return new TableColumnDto();
  }

  private static SingleReportTargetValueDto defaultTargetValue() {
    return new SingleReportTargetValueDto();
  }

  private static HeatmapTargetValueDto defaultHeatmapTargetValue() {
    return new HeatmapTargetValueDto();
  }

  private static AggregateByDateUnit defaultGroupByDateVariableUnit() {
    return AggregateByDateUnit.AUTOMATIC;
  }

  private static AggregateByDateUnit defaultDistributeByDateVariableUnit() {
    return AggregateByDateUnit.AUTOMATIC;
  }

  private static CustomBucketDto defaultCustomBucket() {
    return CustomBucketDto.builder().build();
  }

  private static CustomBucketDto defaultDistributeByCustomBucket() {
    return CustomBucketDto.builder().build();
  }

  private static ReportSortingDto defaultSorting() {
    return null;
  }

  private static ProcessPartDto defaultProcessPart() {
    return null;
  }

  private static MeasureVisualizationsDto defaultMeasureVisualizations() {
    return new MeasureVisualizationsDto();
  }

  private static Boolean defaultStackedBar() {
    return false;
  }

  private static Boolean defaultHorizontalBar() {
    return false;
  }

  private static Boolean defaultLogScale() {
    return false;
  }

  public static SingleReportConfigurationDtoBuilder builder() {
    return new SingleReportConfigurationDtoBuilder();
  }

  @SuppressWarnings("checkstyle:ConstantName")
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

    private String colorValue;
    private boolean colorSet;
    private Set<AggregationDto> aggregationTypesValue;
    private boolean aggregationTypesSet;
    private Set<UserTaskDurationTime> userTaskDurationTimesValue;
    private boolean userTaskDurationTimesSet;
    private Boolean showInstanceCountValue;
    private boolean showInstanceCountSet;
    private Boolean pointMarkersValue;
    private boolean pointMarkersSet;
    private Integer precisionValue;
    private boolean precisionSet;
    private Boolean hideRelativeValueValue;
    private boolean hideRelativeValueSet;
    private Boolean hideAbsoluteValueValue;
    private boolean hideAbsoluteValueSet;
    private String yLabelValue;
    private boolean yLabelSet;
    private String xLabelValue;
    private boolean xLabelSet;
    private Boolean alwaysShowRelativeValue;
    private boolean alwaysShowRelativeSet;
    private Boolean alwaysShowAbsoluteValue;
    private boolean alwaysShowAbsoluteSet;
    private Boolean showGradientBarsValue;
    private boolean showGradientBarsSet;
    private String xmlValue;
    private boolean xmlSet;
    private TableColumnDto tableColumnsValue;
    private boolean tableColumnsSet;
    private SingleReportTargetValueDto targetValueValue;
    private boolean targetValueSet;
    private HeatmapTargetValueDto heatmapTargetValueValue;
    private boolean heatmapTargetValueSet;
    private AggregateByDateUnit groupByDateVariableUnitValue;
    private boolean groupByDateVariableUnitSet;
    private AggregateByDateUnit distributeByDateVariableUnitValue;
    private boolean distributeByDateVariableUnitSet;
    private CustomBucketDto customBucketValue;
    private boolean customBucketSet;
    private CustomBucketDto distributeByCustomBucketValue;
    private boolean distributeByCustomBucketSet;
    private ReportSortingDto sortingValue;
    private boolean sortingSet;
    private ProcessPartDto processPartValue;
    private boolean processPartSet;
    private MeasureVisualizationsDto measureVisualizationsValue;
    private boolean measureVisualizationsSet;
    private Boolean stackedBarValue;
    private boolean stackedBarSet;
    private Boolean horizontalBarValue;
    private boolean horizontalBarSet;
    private Boolean logScaleValue;
    private boolean logScaleSet;

    SingleReportConfigurationDtoBuilder() {}

    public SingleReportConfigurationDtoBuilder color(final String color) {
      colorValue = color;
      colorSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder aggregationTypes(
        final Set<AggregationDto> aggregationTypes) {
      aggregationTypesValue = aggregationTypes;
      aggregationTypesSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder userTaskDurationTimes(
        final Set<UserTaskDurationTime> userTaskDurationTimes) {
      userTaskDurationTimesValue = userTaskDurationTimes;
      userTaskDurationTimesSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder showInstanceCount(final Boolean showInstanceCount) {
      showInstanceCountValue = showInstanceCount;
      showInstanceCountSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder pointMarkers(final Boolean pointMarkers) {
      pointMarkersValue = pointMarkers;
      pointMarkersSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder precision(final Integer precision) {
      precisionValue = precision;
      precisionSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder hideRelativeValue(final Boolean hideRelativeValue) {
      hideRelativeValueValue = hideRelativeValue;
      hideRelativeValueSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder hideAbsoluteValue(final Boolean hideAbsoluteValue) {
      hideAbsoluteValueValue = hideAbsoluteValue;
      hideAbsoluteValueSet = true;
      return this;
    }

    @JsonProperty("yLabel")
    public SingleReportConfigurationDtoBuilder yLabel(final String yLabel) {
      yLabelValue = yLabel;
      yLabelSet = true;
      return this;
    }

    @JsonProperty("xLabel")
    public SingleReportConfigurationDtoBuilder xLabel(final String xLabel) {
      xLabelValue = xLabel;
      xLabelSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder alwaysShowRelative(
        final Boolean alwaysShowRelative) {
      alwaysShowRelativeValue = alwaysShowRelative;
      alwaysShowRelativeSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder alwaysShowAbsolute(
        final Boolean alwaysShowAbsolute) {
      alwaysShowAbsoluteValue = alwaysShowAbsolute;
      alwaysShowAbsoluteSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder showGradientBars(final Boolean showGradientBars) {
      showGradientBarsValue = showGradientBars;
      showGradientBarsSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder xml(final String xml) {
      xmlValue = xml;
      xmlSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder tableColumns(final TableColumnDto tableColumns) {
      tableColumnsValue = tableColumns;
      tableColumnsSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder targetValue(
        final SingleReportTargetValueDto targetValue) {
      targetValueValue = targetValue;
      targetValueSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder heatmapTargetValue(
        final HeatmapTargetValueDto heatmapTargetValue) {
      heatmapTargetValueValue = heatmapTargetValue;
      heatmapTargetValueSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder groupByDateVariableUnit(
        final AggregateByDateUnit groupByDateVariableUnit) {
      if (groupByDateVariableUnit == null) {
        throw new IllegalArgumentException("groupByDateVariableUnit must not be null");
      }

      groupByDateVariableUnitValue = groupByDateVariableUnit;
      groupByDateVariableUnitSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder distributeByDateVariableUnit(
        final AggregateByDateUnit distributeByDateVariableUnit) {
      if (distributeByDateVariableUnit == null) {
        throw new IllegalArgumentException("distributeByDateVariableUnit must not be null");
      }

      distributeByDateVariableUnitValue = distributeByDateVariableUnit;
      distributeByDateVariableUnitSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder customBucket(final CustomBucketDto customBucket) {
      customBucketValue = customBucket;
      customBucketSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder distributeByCustomBucket(
        final CustomBucketDto distributeByCustomBucket) {
      distributeByCustomBucketValue = distributeByCustomBucket;
      distributeByCustomBucketSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder sorting(final ReportSortingDto sorting) {
      sortingValue = sorting;
      sortingSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder processPart(final ProcessPartDto processPart) {
      processPartValue = processPart;
      processPartSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder measureVisualizations(
        final MeasureVisualizationsDto measureVisualizations) {
      measureVisualizationsValue = measureVisualizations;
      measureVisualizationsSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder stackedBar(final Boolean stackedBar) {
      stackedBarValue = stackedBar;
      stackedBarSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder horizontalBar(final Boolean horizontalBar) {
      horizontalBarValue = horizontalBar;
      horizontalBarSet = true;
      return this;
    }

    public SingleReportConfigurationDtoBuilder logScale(final Boolean logScale) {
      logScaleValue = logScale;
      logScaleSet = true;
      return this;
    }

    public SingleReportConfigurationDto build() {
      String colorValue = this.colorValue;
      if (!colorSet) {
        colorValue = SingleReportConfigurationDto.defaultColor();
      }
      Set<AggregationDto> aggregationTypesValue = this.aggregationTypesValue;
      if (!aggregationTypesSet) {
        aggregationTypesValue = SingleReportConfigurationDto.defaultAggregationTypes();
      }
      Set<UserTaskDurationTime> userTaskDurationTimesValue = this.userTaskDurationTimesValue;
      if (!userTaskDurationTimesSet) {
        userTaskDurationTimesValue = SingleReportConfigurationDto.defaultUserTaskDurationTimes();
      }
      Boolean showInstanceCountValue = this.showInstanceCountValue;
      if (!showInstanceCountSet) {
        showInstanceCountValue = SingleReportConfigurationDto.defaultShowInstanceCount();
      }
      Boolean pointMarkersValue = this.pointMarkersValue;
      if (!pointMarkersSet) {
        pointMarkersValue = SingleReportConfigurationDto.defaultPointMarkers();
      }
      Integer precisionValue = this.precisionValue;
      if (!precisionSet) {
        precisionValue = SingleReportConfigurationDto.defaultPrecision();
      }
      Boolean hideRelativeValueValue = this.hideRelativeValueValue;
      if (!hideRelativeValueSet) {
        hideRelativeValueValue = SingleReportConfigurationDto.defaultHideRelativeValue();
      }
      Boolean hideAbsoluteValueValue = this.hideAbsoluteValueValue;
      if (!hideAbsoluteValueSet) {
        hideAbsoluteValueValue = SingleReportConfigurationDto.defaultHideAbsoluteValue();
      }
      String yLabelValue = this.yLabelValue;
      if (!yLabelSet) {
        yLabelValue = SingleReportConfigurationDto.defaultYLabel();
      }
      String xLabelValue = this.xLabelValue;
      if (!xLabelSet) {
        xLabelValue = SingleReportConfigurationDto.defaultXLabel();
      }
      Boolean alwaysShowRelativeValue = this.alwaysShowRelativeValue;
      if (!alwaysShowRelativeSet) {
        alwaysShowRelativeValue = SingleReportConfigurationDto.defaultAlwaysShowRelative();
      }
      Boolean alwaysShowAbsoluteValue = this.alwaysShowAbsoluteValue;
      if (!alwaysShowAbsoluteSet) {
        alwaysShowAbsoluteValue = SingleReportConfigurationDto.defaultAlwaysShowAbsolute();
      }
      Boolean showGradientBarsValue = this.showGradientBarsValue;
      if (!showGradientBarsSet) {
        showGradientBarsValue = SingleReportConfigurationDto.defaultShowGradientBars();
      }
      String xmlValue = this.xmlValue;
      if (!xmlSet) {
        xmlValue = SingleReportConfigurationDto.defaultXml();
      }
      TableColumnDto tableColumnsValue = this.tableColumnsValue;
      if (!tableColumnsSet) {
        tableColumnsValue = SingleReportConfigurationDto.defaultTableColumns();
      }
      SingleReportTargetValueDto targetValueValue = this.targetValueValue;
      if (!targetValueSet) {
        targetValueValue = SingleReportConfigurationDto.defaultTargetValue();
      }
      HeatmapTargetValueDto heatmapTargetValueValue = this.heatmapTargetValueValue;
      if (!heatmapTargetValueSet) {
        heatmapTargetValueValue = SingleReportConfigurationDto.defaultHeatmapTargetValue();
      }
      AggregateByDateUnit groupByDateVariableUnitValue = this.groupByDateVariableUnitValue;
      if (!groupByDateVariableUnitSet) {
        groupByDateVariableUnitValue =
            SingleReportConfigurationDto.defaultGroupByDateVariableUnit();
      }
      AggregateByDateUnit distributeByDateVariableUnitValue =
          this.distributeByDateVariableUnitValue;
      if (!distributeByDateVariableUnitSet) {
        distributeByDateVariableUnitValue =
            SingleReportConfigurationDto.defaultDistributeByDateVariableUnit();
      }
      CustomBucketDto customBucketValue = this.customBucketValue;
      if (!customBucketSet) {
        customBucketValue = SingleReportConfigurationDto.defaultCustomBucket();
      }
      CustomBucketDto distributeByCustomBucketValue = this.distributeByCustomBucketValue;
      if (!distributeByCustomBucketSet) {
        distributeByCustomBucketValue =
            SingleReportConfigurationDto.defaultDistributeByCustomBucket();
      }
      ReportSortingDto sortingValue = this.sortingValue;
      if (!sortingSet) {
        sortingValue = SingleReportConfigurationDto.defaultSorting();
      }
      ProcessPartDto processPartValue = this.processPartValue;
      if (!processPartSet) {
        processPartValue = SingleReportConfigurationDto.defaultProcessPart();
      }
      MeasureVisualizationsDto measureVisualizationsValue = this.measureVisualizationsValue;
      if (!measureVisualizationsSet) {
        measureVisualizationsValue = SingleReportConfigurationDto.defaultMeasureVisualizations();
      }
      Boolean stackedBarValue = this.stackedBarValue;
      if (!stackedBarSet) {
        stackedBarValue = SingleReportConfigurationDto.defaultStackedBar();
      }
      Boolean horizontalBarValue = this.horizontalBarValue;
      if (!horizontalBarSet) {
        horizontalBarValue = SingleReportConfigurationDto.defaultHorizontalBar();
      }
      Boolean logScaleValue = this.logScaleValue;
      if (!logScaleSet) {
        logScaleValue = SingleReportConfigurationDto.defaultLogScale();
      }
      return new SingleReportConfigurationDto(
          colorValue,
          aggregationTypesValue,
          userTaskDurationTimesValue,
          showInstanceCountValue,
          pointMarkersValue,
          precisionValue,
          hideRelativeValueValue,
          hideAbsoluteValueValue,
          yLabelValue,
          xLabelValue,
          alwaysShowRelativeValue,
          alwaysShowAbsoluteValue,
          showGradientBarsValue,
          xmlValue,
          tableColumnsValue,
          targetValueValue,
          heatmapTargetValueValue,
          groupByDateVariableUnitValue,
          distributeByDateVariableUnitValue,
          customBucketValue,
          distributeByCustomBucketValue,
          sortingValue,
          processPartValue,
          measureVisualizationsValue,
          stackedBarValue,
          horizontalBarValue,
          logScaleValue);
    }

    @Override
    public String toString() {
      return "SingleReportConfigurationDto.SingleReportConfigurationDtoBuilder(colorValue="
          + colorValue
          + ", aggregationTypesValue="
          + aggregationTypesValue
          + ", userTaskDurationTimesValue="
          + userTaskDurationTimesValue
          + ", showInstanceCountValue="
          + showInstanceCountValue
          + ", pointMarkersValue="
          + pointMarkersValue
          + ", precisionValue="
          + precisionValue
          + ", hideRelativeValueValue="
          + hideRelativeValueValue
          + ", hideAbsoluteValueValue="
          + hideAbsoluteValueValue
          + ", yLabelValue="
          + yLabelValue
          + ", xLabelValue="
          + xLabelValue
          + ", alwaysShowRelativeValue="
          + alwaysShowRelativeValue
          + ", alwaysShowAbsoluteValue="
          + alwaysShowAbsoluteValue
          + ", showGradientBarsValue="
          + showGradientBarsValue
          + ", xmlValue="
          + xmlValue
          + ", tableColumnsValue="
          + tableColumnsValue
          + ", targetValueValue="
          + targetValueValue
          + ", heatmapTargetValueValue="
          + heatmapTargetValueValue
          + ", groupByDateVariableUnitValue="
          + groupByDateVariableUnitValue
          + ", distributeByDateVariableUnitValue="
          + distributeByDateVariableUnitValue
          + ", customBucketValue="
          + customBucketValue
          + ", distributeByCustomBucketValue="
          + distributeByCustomBucketValue
          + ", sortingValue="
          + sortingValue
          + ", processPartValue="
          + processPartValue
          + ", measureVisualizationsValue="
          + measureVisualizationsValue
          + ", stackedBarValue="
          + stackedBarValue
          + ", horizontalBarValue="
          + horizontalBarValue
          + ", logScaleValue="
          + logScaleValue
          + ")";
    }
  }
}
