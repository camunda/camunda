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

  protected DateAggregationContext(DateAggregationContextBuilder<?, ?> b) {
    this.aggregateByDateUnit = b.aggregateByDateUnit;
    if (aggregateByDateUnit == null) {
      throw new IllegalArgumentException("aggregateByDateUnit cannot be null");
    }

    this.dateField = b.dateField;
    if (dateField == null) {
      throw new IllegalArgumentException("dateField cannot be null");
    }

    this.runningDateReportEndDateField = b.runningDateReportEndDateField;
    this.minMaxStats = b.minMaxStats;
    if (minMaxStats == null) {
      throw new IllegalArgumentException("minMaxStats cannot be null");
    }

    if (b.extendBoundsToMinMaxStats$set) {
      this.extendBoundsToMinMaxStats = b.extendBoundsToMinMaxStats$value;
    } else {
      this.extendBoundsToMinMaxStats = $default$extendBoundsToMinMaxStats();
    }
    this.timezone = b.timezone;
    if (timezone == null) {
      throw new IllegalArgumentException("timezone cannot be null");
    }

    this.dateAggregationName = b.dateAggregationName;
    this.decisionFilters = b.decisionFilters;
    this.processGroupByType = b.processGroupByType;
    this.distributedByType = b.distributedByType;
    this.processFilters = b.processFilters;
    this.filterContext = b.filterContext;
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
    return this.aggregateByDateUnit;
  }

  public String getDateField() {
    return this.dateField;
  }

  public String getRunningDateReportEndDateField() {
    return this.runningDateReportEndDateField;
  }

  public MinMaxStatDto getMinMaxStats() {
    return this.minMaxStats;
  }

  public boolean isExtendBoundsToMinMaxStats() {
    return this.extendBoundsToMinMaxStats;
  }

  public ZoneId getTimezone() {
    return this.timezone;
  }

  public List<DecisionFilterDto<?>> getDecisionFilters() {
    return this.decisionFilters;
  }

  public ProcessGroupByType getProcessGroupByType() {
    return this.processGroupByType;
  }

  public DistributedByType getDistributedByType() {
    return this.distributedByType;
  }

  public List<ProcessFilterDto<?>> getProcessFilters() {
    return this.processFilters;
  }

  public FilterContext getFilterContext() {
    return this.filterContext;
  }

  public void setAggregateByDateUnit(AggregateByDateUnit aggregateByDateUnit) {
    if (aggregateByDateUnit == null) {
      throw new IllegalArgumentException("aggregateByDateUnit cannot be null");
    }
    this.aggregateByDateUnit = aggregateByDateUnit;
  }

  private static boolean $default$extendBoundsToMinMaxStats() {
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
    private boolean extendBoundsToMinMaxStats$value;
    private boolean extendBoundsToMinMaxStats$set;
    private ZoneId timezone;
    private String dateAggregationName;
    private List<DecisionFilterDto<?>> decisionFilters;
    private ProcessGroupByType processGroupByType;
    private DistributedByType distributedByType;
    private List<ProcessFilterDto<?>> processFilters;
    private FilterContext filterContext;

    public B aggregateByDateUnit(AggregateByDateUnit aggregateByDateUnit) {
      this.aggregateByDateUnit = aggregateByDateUnit;
      if (aggregateByDateUnit == null) {
        throw new IllegalArgumentException("aggregateByDateUnit cannot be null");
      }

      return self();
    }

    public B dateField(String dateField) {
      this.dateField = dateField;
      if (dateField == null) {
        throw new IllegalArgumentException("dateField cannot be null");
      }

      return self();
    }

    public B runningDateReportEndDateField(String runningDateReportEndDateField) {
      this.runningDateReportEndDateField = runningDateReportEndDateField;
      return self();
    }

    public B minMaxStats(MinMaxStatDto minMaxStats) {
      this.minMaxStats = minMaxStats;
      if (minMaxStats == null) {
        throw new IllegalArgumentException("minMaxStats cannot be null");
      }

      return self();
    }

    public B extendBoundsToMinMaxStats(boolean extendBoundsToMinMaxStats) {
      this.extendBoundsToMinMaxStats$value = extendBoundsToMinMaxStats;
      this.extendBoundsToMinMaxStats$set = true;
      return self();
    }

    public B timezone(ZoneId timezone) {
      this.timezone = timezone;
      if (timezone == null) {
        throw new IllegalArgumentException("timezone cannot be null");
      }

      return self();
    }

    public B dateAggregationName(String dateAggregationName) {
      this.dateAggregationName = dateAggregationName;
      return self();
    }

    public B decisionFilters(List<DecisionFilterDto<?>> decisionFilters) {
      this.decisionFilters = decisionFilters;
      return self();
    }

    public B processGroupByType(ProcessGroupByType processGroupByType) {
      this.processGroupByType = processGroupByType;
      return self();
    }

    public B distributedByType(DistributedByType distributedByType) {
      this.distributedByType = distributedByType;
      return self();
    }

    public B processFilters(List<ProcessFilterDto<?>> processFilters) {
      this.processFilters = processFilters;
      return self();
    }

    public B filterContext(FilterContext filterContext) {
      this.filterContext = filterContext;
      if (filterContext == null) {
        throw new IllegalArgumentException("filterContext cannot be null");
      }

      return self();
    }

    protected abstract B self();

    public abstract C build();

    public String toString() {
      return "DateAggregationContext.DateAggregationContextBuilder(aggregateByDateUnit="
          + this.aggregateByDateUnit
          + ", dateField="
          + this.dateField
          + ", runningDateReportEndDateField="
          + this.runningDateReportEndDateField
          + ", minMaxStats="
          + this.minMaxStats
          + ", extendBoundsToMinMaxStats$value="
          + this.extendBoundsToMinMaxStats$value
          + ", timezone="
          + this.timezone
          + ", dateAggregationName="
          + this.dateAggregationName
          + ", decisionFilters="
          + this.decisionFilters
          + ", processGroupByType="
          + this.processGroupByType
          + ", distributedByType="
          + this.distributedByType
          + ", processFilters="
          + this.processFilters
          + ", filterContext="
          + this.filterContext
          + ")";
    }
  }

  private static final class DateAggregationContextBuilderImpl
      extends DateAggregationContextBuilder<
          DateAggregationContext, DateAggregationContextBuilderImpl> {

    private DateAggregationContextBuilderImpl() {}

    protected DateAggregationContextBuilderImpl self() {
      return this;
    }

    public DateAggregationContext build() {
      return new DateAggregationContext(this);
    }
  }
}
