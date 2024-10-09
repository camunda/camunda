/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.context;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import java.time.ZoneId;
import java.util.Optional;

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
  private MinMaxStatDto variableRangeMinMaxStats;
  private final MinMaxStatDto combinedRangeMinMaxStats;
  private final FilterContext filterContext;

  protected VariableAggregationContext(VariableAggregationContextBuilder<?, ?> b) {
    this.variableName = b.variableName;
    this.variableType = b.variableType;
    this.variablePath = b.variablePath;
    this.nestedVariableNameField = b.nestedVariableNameField;
    this.nestedVariableValueFieldLabel = b.nestedVariableValueFieldLabel;
    this.timezone = b.timezone;
    this.customBucketDto = b.customBucketDto;
    this.dateUnit = b.dateUnit;
    this.indexNames = b.indexNames;
    this.variableRangeMinMaxStats = b.variableRangeMinMaxStats;
    this.combinedRangeMinMaxStats = b.combinedRangeMinMaxStats;
    this.filterContext = b.filterContext;
    if (filterContext == null) {
      throw new IllegalArgumentException("FilterContext cannot be null");
    }
  }

  public Optional<MinMaxStatDto> getCombinedRangeMinMaxStats() {
    return Optional.ofNullable(combinedRangeMinMaxStats);
  }

  public double getMaxVariableValue() {
    return getCombinedRangeMinMaxStats().orElse(variableRangeMinMaxStats).getMax();
  }

  public String getVariableName() {
    return this.variableName;
  }

  public VariableType getVariableType() {
    return this.variableType;
  }

  public String getVariablePath() {
    return this.variablePath;
  }

  public String getNestedVariableNameField() {
    return this.nestedVariableNameField;
  }

  public String getNestedVariableValueFieldLabel() {
    return this.nestedVariableValueFieldLabel;
  }

  public ZoneId getTimezone() {
    return this.timezone;
  }

  public CustomBucketDto getCustomBucketDto() {
    return this.customBucketDto;
  }

  public AggregateByDateUnit getDateUnit() {
    return this.dateUnit;
  }

  public String[] getIndexNames() {
    return this.indexNames;
  }

  public MinMaxStatDto getVariableRangeMinMaxStats() {
    return this.variableRangeMinMaxStats;
  }

  public FilterContext getFilterContext() {
    return this.filterContext;
  }

  public void setVariableRangeMinMaxStats(MinMaxStatDto variableRangeMinMaxStats) {
    this.variableRangeMinMaxStats = variableRangeMinMaxStats;
  }

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
    final Object this$variableName = this.getVariableName();
    final Object other$variableName = other.getVariableName();
    if (this$variableName == null
        ? other$variableName != null
        : !this$variableName.equals(other$variableName)) {
      return false;
    }
    final Object this$variableType = this.getVariableType();
    final Object other$variableType = other.getVariableType();
    if (this$variableType == null
        ? other$variableType != null
        : !this$variableType.equals(other$variableType)) {
      return false;
    }
    final Object this$variablePath = this.getVariablePath();
    final Object other$variablePath = other.getVariablePath();
    if (this$variablePath == null
        ? other$variablePath != null
        : !this$variablePath.equals(other$variablePath)) {
      return false;
    }
    final Object this$nestedVariableNameField = this.getNestedVariableNameField();
    final Object other$nestedVariableNameField = other.getNestedVariableNameField();
    if (this$nestedVariableNameField == null
        ? other$nestedVariableNameField != null
        : !this$nestedVariableNameField.equals(other$nestedVariableNameField)) {
      return false;
    }
    final Object this$nestedVariableValueFieldLabel = this.getNestedVariableValueFieldLabel();
    final Object other$nestedVariableValueFieldLabel = other.getNestedVariableValueFieldLabel();
    if (this$nestedVariableValueFieldLabel == null
        ? other$nestedVariableValueFieldLabel != null
        : !this$nestedVariableValueFieldLabel.equals(other$nestedVariableValueFieldLabel)) {
      return false;
    }
    final Object this$timezone = this.getTimezone();
    final Object other$timezone = other.getTimezone();
    if (this$timezone == null ? other$timezone != null : !this$timezone.equals(other$timezone)) {
      return false;
    }
    final Object this$customBucketDto = this.getCustomBucketDto();
    final Object other$customBucketDto = other.getCustomBucketDto();
    if (this$customBucketDto == null
        ? other$customBucketDto != null
        : !this$customBucketDto.equals(other$customBucketDto)) {
      return false;
    }
    final Object this$dateUnit = this.getDateUnit();
    final Object other$dateUnit = other.getDateUnit();
    if (this$dateUnit == null ? other$dateUnit != null : !this$dateUnit.equals(other$dateUnit)) {
      return false;
    }
    if (!java.util.Arrays.deepEquals(this.getIndexNames(), other.getIndexNames())) {
      return false;
    }
    final Object this$variableRangeMinMaxStats = this.getVariableRangeMinMaxStats();
    final Object other$variableRangeMinMaxStats = other.getVariableRangeMinMaxStats();
    if (this$variableRangeMinMaxStats == null
        ? other$variableRangeMinMaxStats != null
        : !this$variableRangeMinMaxStats.equals(other$variableRangeMinMaxStats)) {
      return false;
    }
    final Object this$combinedRangeMinMaxStats = this.getCombinedRangeMinMaxStats();
    final Object other$combinedRangeMinMaxStats = other.getCombinedRangeMinMaxStats();
    if (this$combinedRangeMinMaxStats == null
        ? other$combinedRangeMinMaxStats != null
        : !this$combinedRangeMinMaxStats.equals(other$combinedRangeMinMaxStats)) {
      return false;
    }
    final Object this$filterContext = this.getFilterContext();
    final Object other$filterContext = other.getFilterContext();
    if (this$filterContext == null
        ? other$filterContext != null
        : !this$filterContext.equals(other$filterContext)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof VariableAggregationContext;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $variableName = this.getVariableName();
    result = result * PRIME + ($variableName == null ? 43 : $variableName.hashCode());
    final Object $variableType = this.getVariableType();
    result = result * PRIME + ($variableType == null ? 43 : $variableType.hashCode());
    final Object $variablePath = this.getVariablePath();
    result = result * PRIME + ($variablePath == null ? 43 : $variablePath.hashCode());
    final Object $nestedVariableNameField = this.getNestedVariableNameField();
    result =
        result * PRIME
            + ($nestedVariableNameField == null ? 43 : $nestedVariableNameField.hashCode());
    final Object $nestedVariableValueFieldLabel = this.getNestedVariableValueFieldLabel();
    result =
        result * PRIME
            + ($nestedVariableValueFieldLabel == null
                ? 43
                : $nestedVariableValueFieldLabel.hashCode());
    final Object $timezone = this.getTimezone();
    result = result * PRIME + ($timezone == null ? 43 : $timezone.hashCode());
    final Object $customBucketDto = this.getCustomBucketDto();
    result = result * PRIME + ($customBucketDto == null ? 43 : $customBucketDto.hashCode());
    final Object $dateUnit = this.getDateUnit();
    result = result * PRIME + ($dateUnit == null ? 43 : $dateUnit.hashCode());
    result = result * PRIME + java.util.Arrays.deepHashCode(this.getIndexNames());
    final Object $variableRangeMinMaxStats = this.getVariableRangeMinMaxStats();
    result =
        result * PRIME
            + ($variableRangeMinMaxStats == null ? 43 : $variableRangeMinMaxStats.hashCode());
    final Object $combinedRangeMinMaxStats = this.getCombinedRangeMinMaxStats();
    result =
        result * PRIME
            + ($combinedRangeMinMaxStats == null ? 43 : $combinedRangeMinMaxStats.hashCode());
    final Object $filterContext = this.getFilterContext();
    result = result * PRIME + ($filterContext == null ? 43 : $filterContext.hashCode());
    return result;
  }

  public String toString() {
    return "VariableAggregationContext(variableName="
        + this.getVariableName()
        + ", variableType="
        + this.getVariableType()
        + ", variablePath="
        + this.getVariablePath()
        + ", nestedVariableNameField="
        + this.getNestedVariableNameField()
        + ", nestedVariableValueFieldLabel="
        + this.getNestedVariableValueFieldLabel()
        + ", timezone="
        + this.getTimezone()
        + ", customBucketDto="
        + this.getCustomBucketDto()
        + ", dateUnit="
        + this.getDateUnit()
        + ", indexNames="
        + java.util.Arrays.deepToString(this.getIndexNames())
        + ", variableRangeMinMaxStats="
        + this.getVariableRangeMinMaxStats()
        + ", combinedRangeMinMaxStats="
        + this.getCombinedRangeMinMaxStats()
        + ", filterContext="
        + this.getFilterContext()
        + ")";
  }

  public static VariableAggregationContextBuilder<?, ?> builder() {
    return new VariableAggregationContextBuilderImpl();
  }

  public abstract static class VariableAggregationContextBuilder<
      C extends VariableAggregationContext, B extends VariableAggregationContextBuilder<C, B>> {

    private String variableName;
    private VariableType variableType;
    private String variablePath;
    private String nestedVariableNameField;
    private String nestedVariableValueFieldLabel;
    private ZoneId timezone;
    private CustomBucketDto customBucketDto;
    private AggregateByDateUnit dateUnit;
    private String[] indexNames;
    private MinMaxStatDto variableRangeMinMaxStats;
    private MinMaxStatDto combinedRangeMinMaxStats;
    private FilterContext filterContext;

    public B variableName(String variableName) {
      this.variableName = variableName;
      return self();
    }

    public B variableType(VariableType variableType) {
      this.variableType = variableType;
      return self();
    }

    public B variablePath(String variablePath) {
      this.variablePath = variablePath;
      return self();
    }

    public B nestedVariableNameField(String nestedVariableNameField) {
      this.nestedVariableNameField = nestedVariableNameField;
      return self();
    }

    public B nestedVariableValueFieldLabel(String nestedVariableValueFieldLabel) {
      this.nestedVariableValueFieldLabel = nestedVariableValueFieldLabel;
      return self();
    }

    public B timezone(ZoneId timezone) {
      this.timezone = timezone;
      return self();
    }

    public B customBucketDto(CustomBucketDto customBucketDto) {
      this.customBucketDto = customBucketDto;
      return self();
    }

    public B dateUnit(AggregateByDateUnit dateUnit) {
      this.dateUnit = dateUnit;
      return self();
    }

    public B indexNames(String[] indexNames) {
      this.indexNames = indexNames;
      return self();
    }

    public B variableRangeMinMaxStats(MinMaxStatDto variableRangeMinMaxStats) {
      this.variableRangeMinMaxStats = variableRangeMinMaxStats;
      return self();
    }

    public B combinedRangeMinMaxStats(MinMaxStatDto combinedRangeMinMaxStats) {
      this.combinedRangeMinMaxStats = combinedRangeMinMaxStats;
      return self();
    }

    public B filterContext(FilterContext filterContext) {
      if (filterContext == null) {
        throw new IllegalArgumentException("filterContext must not be null");
      }

      this.filterContext = filterContext;
      return self();
    }

    protected abstract B self();

    public abstract C build();

    public String toString() {
      return "VariableAggregationContext.VariableAggregationContextBuilder(variableName="
          + this.variableName
          + ", variableType="
          + this.variableType
          + ", variablePath="
          + this.variablePath
          + ", nestedVariableNameField="
          + this.nestedVariableNameField
          + ", nestedVariableValueFieldLabel="
          + this.nestedVariableValueFieldLabel
          + ", timezone="
          + this.timezone
          + ", customBucketDto="
          + this.customBucketDto
          + ", dateUnit="
          + this.dateUnit
          + ", indexNames="
          + java.util.Arrays.deepToString(this.indexNames)
          + ", variableRangeMinMaxStats="
          + this.variableRangeMinMaxStats
          + ", combinedRangeMinMaxStats="
          + this.combinedRangeMinMaxStats
          + ", filterContext="
          + this.filterContext
          + ")";
    }
  }

  private static final class VariableAggregationContextBuilderImpl
      extends VariableAggregationContextBuilder<
          VariableAggregationContext, VariableAggregationContextBuilderImpl> {

    private VariableAggregationContextBuilderImpl() {}

    protected VariableAggregationContextBuilderImpl self() {
      return this;
    }

    public VariableAggregationContext build() {
      return new VariableAggregationContext(this);
    }
  }
}
