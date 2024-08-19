/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.util;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import io.camunda.optimize.service.db.es.filter.DecisionQueryFilterEnhancer;
import io.camunda.optimize.service.db.es.filter.FilterContext;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancer;
import io.camunda.optimize.service.db.es.report.MinMaxStatDto;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.elasticsearch.search.aggregations.AggregationBuilder;

public class DateAggregationContext {

  private AggregateByDateUnit aggregateByDateUnit;
  private final String dateField;

  // used for range filter aggregation in running date reports only
  private final String runningDateReportEndDateField;

  private final MinMaxStatDto minMaxStats;

  // extendBoundsToMinMaxStats true is used for distrBy date reports which require extended bounds
  // even when no date
  // filters are applied. If date filters are applied, extendedBounds may be overwritten by the
  // filter bounds.
  // This serves a similar purpose as <allDistributedByKeys> in the ExecutionContext for
  // non-histogram aggregations.
  private boolean extendBoundsToMinMaxStats = false;

  private final ZoneId timezone;
  private final List<AggregationBuilder> subAggregations;
  private final String dateAggregationName;
  private final List<DecisionFilterDto<?>> decisionFilters;
  private final DecisionQueryFilterEnhancer decisionQueryFilterEnhancer;
  private final ProcessGroupByType processGroupByType;
  private final DistributedByType distributedByType;
  private final List<ProcessFilterDto<?>> processFilters;
  private final ProcessQueryFilterEnhancer processQueryFilterEnhancer;
  private final FilterContext filterContext;

  DateAggregationContext(
      final AggregateByDateUnit aggregateByDateUnit,
      final String dateField,
      final String runningDateReportEndDateField,
      final MinMaxStatDto minMaxStats,
      final boolean extendBoundsToMinMaxStats,
      final ZoneId timezone,
      final List<AggregationBuilder> subAggregations,
      final String dateAggregationName,
      final List<DecisionFilterDto<?>> decisionFilters,
      final DecisionQueryFilterEnhancer decisionQueryFilterEnhancer,
      final ProcessGroupByType processGroupByType,
      final DistributedByType distributedByType,
      final List<ProcessFilterDto<?>> processFilters,
      final ProcessQueryFilterEnhancer processQueryFilterEnhancer,
      final FilterContext filterContext) {
    if (aggregateByDateUnit == null) {
      throw new IllegalArgumentException("aggregateByDateUnit cannot be null");
    }
    if (dateField == null) {
      throw new IllegalArgumentException("dateField cannot be null");
    }
    if (minMaxStats == null) {
      throw new IllegalArgumentException("minMaxStats cannot be null");
    }
    if (timezone == null) {
      throw new IllegalArgumentException("timezone cannot be null");
    }
    if (subAggregations == null) {
      throw new IllegalArgumentException("subAggregations cannot be null");
    }
    if (filterContext == null) {
      throw new IllegalArgumentException("filterContext cannot be null");
    }

    this.aggregateByDateUnit = aggregateByDateUnit;
    this.dateField = dateField;
    this.runningDateReportEndDateField = runningDateReportEndDateField;
    this.minMaxStats = minMaxStats;
    this.extendBoundsToMinMaxStats = extendBoundsToMinMaxStats;
    this.timezone = timezone;
    this.subAggregations = subAggregations;
    this.dateAggregationName = dateAggregationName;
    this.decisionFilters = decisionFilters;
    this.decisionQueryFilterEnhancer = decisionQueryFilterEnhancer;
    this.processGroupByType = processGroupByType;
    this.distributedByType = distributedByType;
    this.processFilters = processFilters;
    this.processQueryFilterEnhancer = processQueryFilterEnhancer;
    this.filterContext = filterContext;
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

  public List<AggregationBuilder> getSubAggregations() {
    return subAggregations;
  }

  public List<DecisionFilterDto<?>> getDecisionFilters() {
    return decisionFilters;
  }

  public DecisionQueryFilterEnhancer getDecisionQueryFilterEnhancer() {
    return decisionQueryFilterEnhancer;
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

  public ProcessQueryFilterEnhancer getProcessQueryFilterEnhancer() {
    return processQueryFilterEnhancer;
  }

  public FilterContext getFilterContext() {
    return filterContext;
  }

  private static boolean $default$extendBoundsToMinMaxStats() {
    return false;
  }

  public static DateAggregationContextBuilder builder() {
    return new DateAggregationContextBuilder();
  }

  public static class DateAggregationContextBuilder {

    private AggregateByDateUnit aggregateByDateUnit;
    private String dateField;
    private String runningDateReportEndDateField;
    private MinMaxStatDto minMaxStats;
    private boolean extendBoundsToMinMaxStats$value;
    private boolean extendBoundsToMinMaxStats$set;
    private ZoneId timezone;
    private List<AggregationBuilder> subAggregations;
    private String dateAggregationName;
    private List<DecisionFilterDto<?>> decisionFilters;
    private DecisionQueryFilterEnhancer decisionQueryFilterEnhancer;
    private ProcessGroupByType processGroupByType;
    private DistributedByType distributedByType;
    private List<ProcessFilterDto<?>> processFilters;
    private ProcessQueryFilterEnhancer processQueryFilterEnhancer;
    private FilterContext filterContext;

    DateAggregationContextBuilder() {}

    public DateAggregationContextBuilder aggregateByDateUnit(
        final AggregateByDateUnit aggregateByDateUnit) {
      if (aggregateByDateUnit == null) {
        throw new IllegalArgumentException("aggregateByDateUnit cannot be null");
      }

      this.aggregateByDateUnit = aggregateByDateUnit;
      return this;
    }

    public DateAggregationContextBuilder dateField(final String dateField) {
      if (dateField == null) {
        throw new IllegalArgumentException("dateField cannot be null");
      }

      this.dateField = dateField;
      return this;
    }

    public DateAggregationContextBuilder runningDateReportEndDateField(
        final String runningDateReportEndDateField) {
      this.runningDateReportEndDateField = runningDateReportEndDateField;
      return this;
    }

    public DateAggregationContextBuilder minMaxStats(final MinMaxStatDto minMaxStats) {
      if (minMaxStats == null) {
        throw new IllegalArgumentException("minMaxStats cannot be null");
      }

      this.minMaxStats = minMaxStats;
      return this;
    }

    public DateAggregationContextBuilder extendBoundsToMinMaxStats(
        final boolean extendBoundsToMinMaxStats) {
      extendBoundsToMinMaxStats$value = extendBoundsToMinMaxStats;
      extendBoundsToMinMaxStats$set = true;
      return this;
    }

    public DateAggregationContextBuilder timezone(final ZoneId timezone) {
      if (timezone == null) {
        throw new IllegalArgumentException("timezone cannot be null");
      }

      this.timezone = timezone;
      return this;
    }

    public DateAggregationContextBuilder subAggregations(
        final List<AggregationBuilder> subAggregations) {
      if (subAggregations == null) {
        throw new IllegalArgumentException("subAggregations cannot be null");
      }

      this.subAggregations = subAggregations;
      return this;
    }

    public DateAggregationContextBuilder dateAggregationName(final String dateAggregationName) {
      this.dateAggregationName = dateAggregationName;
      return this;
    }

    public DateAggregationContextBuilder decisionFilters(
        final List<DecisionFilterDto<?>> decisionFilters) {
      this.decisionFilters = decisionFilters;
      return this;
    }

    public DateAggregationContextBuilder decisionQueryFilterEnhancer(
        final DecisionQueryFilterEnhancer decisionQueryFilterEnhancer) {
      this.decisionQueryFilterEnhancer = decisionQueryFilterEnhancer;
      return this;
    }

    public DateAggregationContextBuilder processGroupByType(
        final ProcessGroupByType processGroupByType) {
      this.processGroupByType = processGroupByType;
      return this;
    }

    public DateAggregationContextBuilder distributedByType(
        final DistributedByType distributedByType) {
      this.distributedByType = distributedByType;
      return this;
    }

    public DateAggregationContextBuilder processFilters(
        final List<ProcessFilterDto<?>> processFilters) {
      this.processFilters = processFilters;
      return this;
    }

    public DateAggregationContextBuilder processQueryFilterEnhancer(
        final ProcessQueryFilterEnhancer processQueryFilterEnhancer) {
      this.processQueryFilterEnhancer = processQueryFilterEnhancer;
      return this;
    }

    public DateAggregationContextBuilder filterContext(final FilterContext filterContext) {
      if (filterContext == null) {
        throw new IllegalArgumentException("filterContext cannot be null");
      }

      this.filterContext = filterContext;
      return this;
    }

    public DateAggregationContext build() {
      boolean extendBoundsToMinMaxStats$value = this.extendBoundsToMinMaxStats$value;
      if (!extendBoundsToMinMaxStats$set) {
        extendBoundsToMinMaxStats$value =
            DateAggregationContext.$default$extendBoundsToMinMaxStats();
      }
      return new DateAggregationContext(
          aggregateByDateUnit,
          dateField,
          runningDateReportEndDateField,
          minMaxStats,
          extendBoundsToMinMaxStats$value,
          timezone,
          subAggregations,
          dateAggregationName,
          decisionFilters,
          decisionQueryFilterEnhancer,
          processGroupByType,
          distributedByType,
          processFilters,
          processQueryFilterEnhancer,
          filterContext);
    }

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
          + ", extendBoundsToMinMaxStats$value="
          + extendBoundsToMinMaxStats$value
          + ", timezone="
          + timezone
          + ", subAggregations="
          + subAggregations
          + ", dateAggregationName="
          + dateAggregationName
          + ", decisionFilters="
          + decisionFilters
          + ", decisionQueryFilterEnhancer="
          + decisionQueryFilterEnhancer
          + ", processGroupByType="
          + processGroupByType
          + ", distributedByType="
          + distributedByType
          + ", processFilters="
          + processFilters
          + ", processQueryFilterEnhancer="
          + processQueryFilterEnhancer
          + ", filterContext="
          + filterContext
          + ")";
    }
  }
}
