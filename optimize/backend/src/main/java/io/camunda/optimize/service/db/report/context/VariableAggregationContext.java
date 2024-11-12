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

  protected VariableAggregationContext(final VariableAggregationContextBuilder<?, ?> b) {
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

  public void setVariableRangeMinMaxStats(final MinMaxStatDto variableRangeMinMaxStats) {
    this.variableRangeMinMaxStats = variableRangeMinMaxStats;
  }

  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof VariableAggregationContext;
  }

  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
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

    public B variableName(final String variableName) {
      this.variableName = variableName;
      return self();
    }

    public B variableType(final VariableType variableType) {
      this.variableType = variableType;
      return self();
    }

    public B variablePath(final String variablePath) {
      this.variablePath = variablePath;
      return self();
    }

    public B nestedVariableNameField(final String nestedVariableNameField) {
      this.nestedVariableNameField = nestedVariableNameField;
      return self();
    }

    public B nestedVariableValueFieldLabel(final String nestedVariableValueFieldLabel) {
      this.nestedVariableValueFieldLabel = nestedVariableValueFieldLabel;
      return self();
    }

    public B timezone(final ZoneId timezone) {
      this.timezone = timezone;
      return self();
    }

    public B customBucketDto(final CustomBucketDto customBucketDto) {
      this.customBucketDto = customBucketDto;
      return self();
    }

    public B dateUnit(final AggregateByDateUnit dateUnit) {
      this.dateUnit = dateUnit;
      return self();
    }

    public B indexNames(final String[] indexNames) {
      this.indexNames = indexNames;
      return self();
    }

    public B variableRangeMinMaxStats(final MinMaxStatDto variableRangeMinMaxStats) {
      this.variableRangeMinMaxStats = variableRangeMinMaxStats;
      return self();
    }

    public B combinedRangeMinMaxStats(final MinMaxStatDto combinedRangeMinMaxStats) {
      this.combinedRangeMinMaxStats = combinedRangeMinMaxStats;
      return self();
    }

    public B filterContext(final FilterContext filterContext) {
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
