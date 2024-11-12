/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.context;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public class DateAggregationContext {

  private AggregateByDateUnit aggregateByDateUnit;
  private final String dateField;
  private final String
      runningDateReportEndDateField; // used for range filter aggregation in running date reports
  // only

  private final MinMaxStatDto minMaxStats;
  // extendBoundsToMinMaxStats true is used for distrBy date reports which require extended bounds
  // even when no date
  // filters are applied. If date filters are applied, extendedBounds may be overwritten by the
  // filter bounds.
  // This serves a similar purpose as <allDistributedByKeys> in the ExecutionContext for
  // non-histogram aggregations.
  private boolean extendBoundsToMinMaxStats = false;

  private final ZoneId timezone;

  private final String dateAggregationName;

  private final List<DecisionFilterDto<?>> decisionFilters;

  private final ProcessGroupByType processGroupByType;
  private final DistributedByType distributedByType;
  private final List<ProcessFilterDto<?>> processFilters;
  private final FilterContext filterContext;

  protected DateAggregationContext(final DateAggregationContextBuilder<?, ?> b) {
    aggregateByDateUnit = b.aggregateByDateUnit;
    if (aggregateByDateUnit == null) {
      throw new IllegalArgumentException("aggregateByDateUnit cannot be null");
    }

    dateField = b.dateField;
    if (dateField == null) {
      throw new IllegalArgumentException("dateField cannot be null");
    }

    runningDateReportEndDateField = b.runningDateReportEndDateField;
    minMaxStats = b.minMaxStats;
    if (minMaxStats == null) {
      throw new IllegalArgumentException("minMaxStats cannot be null");
    }

    if (b.extendBoundsToMinMaxStatsSet) {
      extendBoundsToMinMaxStats = b.extendBoundsToMinMaxStatsValue;
    } else {
      extendBoundsToMinMaxStats = defaultExtendBoundsToMinMaxStats();
    }
    timezone = b.timezone;
    if (timezone == null) {
      throw new IllegalArgumentException("timezone cannot be null");
    }

    dateAggregationName = b.dateAggregationName;
    decisionFilters = b.decisionFilters;
    processGroupByType = b.processGroupByType;
    distributedByType = b.distributedByType;
    processFilters = b.processFilters;
    filterContext = b.filterContext;
    if (filterContext == null) {
      throw new IllegalArgumentException("filterContext cannot be null");
    }
  }

  public ZonedDateTime getEarliestDate() {
    return ZonedDateTime.ofInstant(
        Instant.ofEpochMilli(Math.round(minMaxStats.getMin())), timezone);
  }

  public ZonedDateTime getLatestDate() {
    return ZonedDateTime.ofInstant(
        Instant.ofEpochMilli(Math.round(minMaxStats.getMax())), timezone);
  }

  public Optional<String> getDateAggregationName() {
    return Optional.ofNullable(dateAggregationName);
  }

  public boolean isStartDateAggregation() {
    if (processGroupByType != null) {
      return ProcessGroupByType.START_DATE.equals(processGroupByType);
    } else {
      return DistributedByType.START_DATE.equals(distributedByType);
    }
  }

  public AggregateByDateUnit getAggregateByDateUnit() {
    return aggregateByDateUnit;
  }

  public void setAggregateByDateUnit(final AggregateByDateUnit aggregateByDateUnit) {
    if (aggregateByDateUnit == null) {
      throw new IllegalArgumentException("aggregateByDateUnit cannot be null");
    }
    this.aggregateByDateUnit = aggregateByDateUnit;
  }

  public String getDateField() {
    return dateField;
  }

  public String getRunningDateReportEndDateField() {
    return runningDateReportEndDateField;
  }

  public MinMaxStatDto getMinMaxStats() {
    return minMaxStats;
  }

  public boolean isExtendBoundsToMinMaxStats() {
    return extendBoundsToMinMaxStats;
  }

  public ZoneId getTimezone() {
    return timezone;
  }

  public List<DecisionFilterDto<?>> getDecisionFilters() {
    return decisionFilters;
  }

  public ProcessGroupByType getProcessGroupByType() {
    return processGroupByType;
  }

  public DistributedByType getDistributedByType() {
    return distributedByType;
  }

  public List<ProcessFilterDto<?>> getProcessFilters() {
    return processFilters;
  }

  public FilterContext getFilterContext() {
    return filterContext;
  }

  private static boolean defaultExtendBoundsToMinMaxStats() {
    return false;
  }

  public static DateAggregationContextBuilder<?, ?> builder() {
    return new DateAggregationContextBuilderImpl();
  }

  public abstract static class DateAggregationContextBuilder<
      C extends DateAggregationContext, B extends DateAggregationContextBuilder<C, B>> {

    private AggregateByDateUnit aggregateByDateUnit;
    private String dateField;
    private String runningDateReportEndDateField;
    private MinMaxStatDto minMaxStats;
    private boolean extendBoundsToMinMaxStatsValue;
    private boolean extendBoundsToMinMaxStatsSet;
    private ZoneId timezone;
    private String dateAggregationName;
    private List<DecisionFilterDto<?>> decisionFilters;
    private ProcessGroupByType processGroupByType;
    private DistributedByType distributedByType;
    private List<ProcessFilterDto<?>> processFilters;
    private FilterContext filterContext;

    public B aggregateByDateUnit(final AggregateByDateUnit aggregateByDateUnit) {
      this.aggregateByDateUnit = aggregateByDateUnit;
      if (aggregateByDateUnit == null) {
        throw new IllegalArgumentException("aggregateByDateUnit cannot be null");
      }

      return self();
    }

    public B dateField(final String dateField) {
      this.dateField = dateField;
      if (dateField == null) {
        throw new IllegalArgumentException("dateField cannot be null");
      }

      return self();
    }

    public B runningDateReportEndDateField(final String runningDateReportEndDateField) {
      this.runningDateReportEndDateField = runningDateReportEndDateField;
      return self();
    }

    public B minMaxStats(final MinMaxStatDto minMaxStats) {
      this.minMaxStats = minMaxStats;
      if (minMaxStats == null) {
        throw new IllegalArgumentException("minMaxStats cannot be null");
      }

      return self();
    }

    public B extendBoundsToMinMaxStats(final boolean extendBoundsToMinMaxStats) {
      extendBoundsToMinMaxStatsValue = extendBoundsToMinMaxStats;
      extendBoundsToMinMaxStatsSet = true;
      return self();
    }

    public B timezone(final ZoneId timezone) {
      this.timezone = timezone;
      if (timezone == null) {
        throw new IllegalArgumentException("timezone cannot be null");
      }

      return self();
    }

    public B dateAggregationName(final String dateAggregationName) {
      this.dateAggregationName = dateAggregationName;
      return self();
    }

    public B decisionFilters(final List<DecisionFilterDto<?>> decisionFilters) {
      this.decisionFilters = decisionFilters;
      return self();
    }

    public B processGroupByType(final ProcessGroupByType processGroupByType) {
      this.processGroupByType = processGroupByType;
      return self();
    }

    public B distributedByType(final DistributedByType distributedByType) {
      this.distributedByType = distributedByType;
      return self();
    }

    public B processFilters(final List<ProcessFilterDto<?>> processFilters) {
      this.processFilters = processFilters;
      return self();
    }

    public B filterContext(final FilterContext filterContext) {
      this.filterContext = filterContext;
      if (filterContext == null) {
        throw new IllegalArgumentException("filterContext cannot be null");
      }

      return self();
    }

    protected abstract B self();

    public abstract C build();

    @Override
    public String toString() {
      return "DateAggregationContext.DateAggregationContextBuilder(aggregateByDateUnit="
          + aggregateByDateUnit
          + ", dateField="
          + dateField
          + ", runningDateReportEndDateField="
          + runningDateReportEndDateField
          + ", minMaxStats="
          + minMaxStats
          + ", extendBoundsToMinMaxStatsValue="
          + extendBoundsToMinMaxStatsValue
          + ", timezone="
          + timezone
          + ", dateAggregationName="
          + dateAggregationName
          + ", decisionFilters="
          + decisionFilters
          + ", processGroupByType="
          + processGroupByType
          + ", distributedByType="
          + distributedByType
          + ", processFilters="
          + processFilters
          + ", filterContext="
          + filterContext
          + ")";
    }
  }

  private static final class DateAggregationContextBuilderImpl
      extends DateAggregationContextBuilder<
          DateAggregationContext, DateAggregationContextBuilderImpl> {

    private DateAggregationContextBuilderImpl() {}

    @Override
    protected DateAggregationContextBuilderImpl self() {
      return this;
    }

    @Override
    public DateAggregationContext build() {
      return new DateAggregationContext(this);
    }
  }
}
