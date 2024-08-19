/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.util;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.es.filter.FilterContext;
import io.camunda.optimize.service.db.es.report.MinMaxStatDto;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;

public class VariableAggregationContext {

  private final String variableName;
  private final VariableType variableType;
  private final String variablePath;
  private final String nestedVariableNameField;
  private final String nestedVariableValueFieldLabel;
  private final ZoneId timezone;
  private final CustomBucketDto customBucketDto;
  private final AggregateByDateUnit dateUnit;
  private final String[] indexNames;
  private final QueryBuilder baseQueryForMinMaxStats;
  private final List<AggregationBuilder> subAggregations;
  private MinMaxStatDto variableRangeMinMaxStats;
  private final MinMaxStatDto combinedRangeMinMaxStats;
  private final FilterContext filterContext;

  VariableAggregationContext(
      final String variableName,
      final VariableType variableType,
      final String variablePath,
      final String nestedVariableNameField,
      final String nestedVariableValueFieldLabel,
      final ZoneId timezone,
      final CustomBucketDto customBucketDto,
      final AggregateByDateUnit dateUnit,
      final String[] indexNames,
      final QueryBuilder baseQueryForMinMaxStats,
      final List<AggregationBuilder> subAggregations,
      final MinMaxStatDto variableRangeMinMaxStats,
      final MinMaxStatDto combinedRangeMinMaxStats,
      final FilterContext filterContext) {
    if (filterContext == null) {
      throw new IllegalArgumentException("filterContext cannot be null");
    }

    this.variableName = variableName;
    this.variableType = variableType;
    this.variablePath = variablePath;
    this.nestedVariableNameField = nestedVariableNameField;
    this.nestedVariableValueFieldLabel = nestedVariableValueFieldLabel;
    this.timezone = timezone;
    this.customBucketDto = customBucketDto;
    this.dateUnit = dateUnit;
    this.indexNames = indexNames;
    this.baseQueryForMinMaxStats = baseQueryForMinMaxStats;
    this.subAggregations = subAggregations;
    this.variableRangeMinMaxStats = variableRangeMinMaxStats;
    this.combinedRangeMinMaxStats = combinedRangeMinMaxStats;
    this.filterContext = filterContext;
  }

  public Optional<MinMaxStatDto> getCombinedRangeMinMaxStats() {
    return Optional.ofNullable(combinedRangeMinMaxStats);
  }

  public double getMaxVariableValue() {
    return getCombinedRangeMinMaxStats().orElse(variableRangeMinMaxStats).getMax();
  }

  public String getVariableName() {
    return variableName;
  }

  public VariableType getVariableType() {
    return variableType;
  }

  public String getVariablePath() {
    return variablePath;
  }

  public String getNestedVariableNameField() {
    return nestedVariableNameField;
  }

  public String getNestedVariableValueFieldLabel() {
    return nestedVariableValueFieldLabel;
  }

  public ZoneId getTimezone() {
    return timezone;
  }

  public CustomBucketDto getCustomBucketDto() {
    return customBucketDto;
  }

  public AggregateByDateUnit getDateUnit() {
    return dateUnit;
  }

  public String[] getIndexNames() {
    return indexNames;
  }

  public QueryBuilder getBaseQueryForMinMaxStats() {
    return baseQueryForMinMaxStats;
  }

  public List<AggregationBuilder> getSubAggregations() {
    return subAggregations;
  }

  public MinMaxStatDto getVariableRangeMinMaxStats() {
    return variableRangeMinMaxStats;
  }

  public void setVariableRangeMinMaxStats(final MinMaxStatDto variableRangeMinMaxStats) {
    this.variableRangeMinMaxStats = variableRangeMinMaxStats;
  }

  public FilterContext getFilterContext() {
    return filterContext;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof VariableAggregationContext;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $variableName = getVariableName();
    result = result * PRIME + ($variableName == null ? 43 : $variableName.hashCode());
    final Object $variableType = getVariableType();
    result = result * PRIME + ($variableType == null ? 43 : $variableType.hashCode());
    final Object $variablePath = getVariablePath();
    result = result * PRIME + ($variablePath == null ? 43 : $variablePath.hashCode());
    final Object $nestedVariableNameField = getNestedVariableNameField();
    result =
        result * PRIME
            + ($nestedVariableNameField == null ? 43 : $nestedVariableNameField.hashCode());
    final Object $nestedVariableValueFieldLabel = getNestedVariableValueFieldLabel();
    result =
        result * PRIME
            + ($nestedVariableValueFieldLabel == null
                ? 43
                : $nestedVariableValueFieldLabel.hashCode());
    final Object $timezone = getTimezone();
    result = result * PRIME + ($timezone == null ? 43 : $timezone.hashCode());
    final Object $customBucketDto = getCustomBucketDto();
    result = result * PRIME + ($customBucketDto == null ? 43 : $customBucketDto.hashCode());
    final Object $dateUnit = getDateUnit();
    result = result * PRIME + ($dateUnit == null ? 43 : $dateUnit.hashCode());
    result = result * PRIME + java.util.Arrays.deepHashCode(getIndexNames());
    final Object $baseQueryForMinMaxStats = getBaseQueryForMinMaxStats();
    result =
        result * PRIME
            + ($baseQueryForMinMaxStats == null ? 43 : $baseQueryForMinMaxStats.hashCode());
    final Object $subAggregations = getSubAggregations();
    result = result * PRIME + ($subAggregations == null ? 43 : $subAggregations.hashCode());
    final Object $variableRangeMinMaxStats = getVariableRangeMinMaxStats();
    result =
        result * PRIME
            + ($variableRangeMinMaxStats == null ? 43 : $variableRangeMinMaxStats.hashCode());
    final Object $combinedRangeMinMaxStats = getCombinedRangeMinMaxStats();
    result =
        result * PRIME
            + ($combinedRangeMinMaxStats == null ? 43 : $combinedRangeMinMaxStats.hashCode());
    final Object $filterContext = getFilterContext();
    result = result * PRIME + ($filterContext == null ? 43 : $filterContext.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof VariableAggregationContext)) {
      return false;
    }
    final VariableAggregationContext other = (VariableAggregationContext) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$variableName = getVariableName();
    final Object other$variableName = other.getVariableName();
    if (this$variableName == null
        ? other$variableName != null
        : !this$variableName.equals(other$variableName)) {
      return false;
    }
    final Object this$variableType = getVariableType();
    final Object other$variableType = other.getVariableType();
    if (this$variableType == null
        ? other$variableType != null
        : !this$variableType.equals(other$variableType)) {
      return false;
    }
    final Object this$variablePath = getVariablePath();
    final Object other$variablePath = other.getVariablePath();
    if (this$variablePath == null
        ? other$variablePath != null
        : !this$variablePath.equals(other$variablePath)) {
      return false;
    }
    final Object this$nestedVariableNameField = getNestedVariableNameField();
    final Object other$nestedVariableNameField = other.getNestedVariableNameField();
    if (this$nestedVariableNameField == null
        ? other$nestedVariableNameField != null
        : !this$nestedVariableNameField.equals(other$nestedVariableNameField)) {
      return false;
    }
    final Object this$nestedVariableValueFieldLabel = getNestedVariableValueFieldLabel();
    final Object other$nestedVariableValueFieldLabel = other.getNestedVariableValueFieldLabel();
    if (this$nestedVariableValueFieldLabel == null
        ? other$nestedVariableValueFieldLabel != null
        : !this$nestedVariableValueFieldLabel.equals(other$nestedVariableValueFieldLabel)) {
      return false;
    }
    final Object this$timezone = getTimezone();
    final Object other$timezone = other.getTimezone();
    if (this$timezone == null ? other$timezone != null : !this$timezone.equals(other$timezone)) {
      return false;
    }
    final Object this$customBucketDto = getCustomBucketDto();
    final Object other$customBucketDto = other.getCustomBucketDto();
    if (this$customBucketDto == null
        ? other$customBucketDto != null
        : !this$customBucketDto.equals(other$customBucketDto)) {
      return false;
    }
    final Object this$dateUnit = getDateUnit();
    final Object other$dateUnit = other.getDateUnit();
    if (this$dateUnit == null ? other$dateUnit != null : !this$dateUnit.equals(other$dateUnit)) {
      return false;
    }
    if (!java.util.Arrays.deepEquals(getIndexNames(), other.getIndexNames())) {
      return false;
    }
    final Object this$baseQueryForMinMaxStats = getBaseQueryForMinMaxStats();
    final Object other$baseQueryForMinMaxStats = other.getBaseQueryForMinMaxStats();
    if (this$baseQueryForMinMaxStats == null
        ? other$baseQueryForMinMaxStats != null
        : !this$baseQueryForMinMaxStats.equals(other$baseQueryForMinMaxStats)) {
      return false;
    }
    final Object this$subAggregations = getSubAggregations();
    final Object other$subAggregations = other.getSubAggregations();
    if (this$subAggregations == null
        ? other$subAggregations != null
        : !this$subAggregations.equals(other$subAggregations)) {
      return false;
    }
    final Object this$variableRangeMinMaxStats = getVariableRangeMinMaxStats();
    final Object other$variableRangeMinMaxStats = other.getVariableRangeMinMaxStats();
    if (this$variableRangeMinMaxStats == null
        ? other$variableRangeMinMaxStats != null
        : !this$variableRangeMinMaxStats.equals(other$variableRangeMinMaxStats)) {
      return false;
    }
    final Object this$combinedRangeMinMaxStats = getCombinedRangeMinMaxStats();
    final Object other$combinedRangeMinMaxStats = other.getCombinedRangeMinMaxStats();
    if (this$combinedRangeMinMaxStats == null
        ? other$combinedRangeMinMaxStats != null
        : !this$combinedRangeMinMaxStats.equals(other$combinedRangeMinMaxStats)) {
      return false;
    }
    final Object this$filterContext = getFilterContext();
    final Object other$filterContext = other.getFilterContext();
    if (this$filterContext == null
        ? other$filterContext != null
        : !this$filterContext.equals(other$filterContext)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "VariableAggregationContext(variableName="
        + getVariableName()
        + ", variableType="
        + getVariableType()
        + ", variablePath="
        + getVariablePath()
        + ", nestedVariableNameField="
        + getNestedVariableNameField()
        + ", nestedVariableValueFieldLabel="
        + getNestedVariableValueFieldLabel()
        + ", timezone="
        + getTimezone()
        + ", customBucketDto="
        + getCustomBucketDto()
        + ", dateUnit="
        + getDateUnit()
        + ", indexNames="
        + java.util.Arrays.deepToString(getIndexNames())
        + ", baseQueryForMinMaxStats="
        + getBaseQueryForMinMaxStats()
        + ", subAggregations="
        + getSubAggregations()
        + ", variableRangeMinMaxStats="
        + getVariableRangeMinMaxStats()
        + ", combinedRangeMinMaxStats="
        + getCombinedRangeMinMaxStats()
        + ", filterContext="
        + getFilterContext()
        + ")";
  }

  public static VariableAggregationContextBuilder builder() {
    return new VariableAggregationContextBuilder();
  }

  public static class VariableAggregationContextBuilder {

    private String variableName;
    private VariableType variableType;
    private String variablePath;
    private String nestedVariableNameField;
    private String nestedVariableValueFieldLabel;
    private ZoneId timezone;
    private CustomBucketDto customBucketDto;
    private AggregateByDateUnit dateUnit;
    private String[] indexNames;
    private QueryBuilder baseQueryForMinMaxStats;
    private List<AggregationBuilder> subAggregations;
    private MinMaxStatDto variableRangeMinMaxStats;
    private MinMaxStatDto combinedRangeMinMaxStats;
    private FilterContext filterContext;

    VariableAggregationContextBuilder() {}

    public VariableAggregationContextBuilder variableName(final String variableName) {
      this.variableName = variableName;
      return this;
    }

    public VariableAggregationContextBuilder variableType(final VariableType variableType) {
      this.variableType = variableType;
      return this;
    }

    public VariableAggregationContextBuilder variablePath(final String variablePath) {
      this.variablePath = variablePath;
      return this;
    }

    public VariableAggregationContextBuilder nestedVariableNameField(
        final String nestedVariableNameField) {
      this.nestedVariableNameField = nestedVariableNameField;
      return this;
    }

    public VariableAggregationContextBuilder nestedVariableValueFieldLabel(
        final String nestedVariableValueFieldLabel) {
      this.nestedVariableValueFieldLabel = nestedVariableValueFieldLabel;
      return this;
    }

    public VariableAggregationContextBuilder timezone(final ZoneId timezone) {
      this.timezone = timezone;
      return this;
    }

    public VariableAggregationContextBuilder customBucketDto(
        final CustomBucketDto customBucketDto) {
      this.customBucketDto = customBucketDto;
      return this;
    }

    public VariableAggregationContextBuilder dateUnit(final AggregateByDateUnit dateUnit) {
      this.dateUnit = dateUnit;
      return this;
    }

    public VariableAggregationContextBuilder indexNames(final String[] indexNames) {
      this.indexNames = indexNames;
      return this;
    }

    public VariableAggregationContextBuilder baseQueryForMinMaxStats(
        final QueryBuilder baseQueryForMinMaxStats) {
      this.baseQueryForMinMaxStats = baseQueryForMinMaxStats;
      return this;
    }

    public VariableAggregationContextBuilder subAggregations(
        final List<AggregationBuilder> subAggregations) {
      this.subAggregations = subAggregations;
      return this;
    }

    public VariableAggregationContextBuilder variableRangeMinMaxStats(
        final MinMaxStatDto variableRangeMinMaxStats) {
      this.variableRangeMinMaxStats = variableRangeMinMaxStats;
      return this;
    }

    public VariableAggregationContextBuilder combinedRangeMinMaxStats(
        final MinMaxStatDto combinedRangeMinMaxStats) {
      this.combinedRangeMinMaxStats = combinedRangeMinMaxStats;
      return this;
    }

    public VariableAggregationContextBuilder filterContext(final FilterContext filterContext) {
      if (filterContext == null) {
        throw new IllegalArgumentException("filterContext cannot be null");
      }

      this.filterContext = filterContext;
      return this;
    }

    public VariableAggregationContext build() {
      return new VariableAggregationContext(
          variableName,
          variableType,
          variablePath,
          nestedVariableNameField,
          nestedVariableValueFieldLabel,
          timezone,
          customBucketDto,
          dateUnit,
          indexNames,
          baseQueryForMinMaxStats,
          subAggregations,
          variableRangeMinMaxStats,
          combinedRangeMinMaxStats,
          filterContext);
    }

    @Override
    public String toString() {
      return "VariableAggregationContext.VariableAggregationContextBuilder(variableName="
          + variableName
          + ", variableType="
          + variableType
          + ", variablePath="
          + variablePath
          + ", nestedVariableNameField="
          + nestedVariableNameField
          + ", nestedVariableValueFieldLabel="
          + nestedVariableValueFieldLabel
          + ", timezone="
          + timezone
          + ", customBucketDto="
          + customBucketDto
          + ", dateUnit="
          + dateUnit
          + ", indexNames="
          + java.util.Arrays.deepToString(indexNames)
          + ", baseQueryForMinMaxStats="
          + baseQueryForMinMaxStats
          + ", subAggregations="
          + subAggregations
          + ", variableRangeMinMaxStats="
          + variableRangeMinMaxStats
          + ", combinedRangeMinMaxStats="
          + combinedRangeMinMaxStats
          + ", filterContext="
          + filterContext
          + ")";
    }
  }
}
