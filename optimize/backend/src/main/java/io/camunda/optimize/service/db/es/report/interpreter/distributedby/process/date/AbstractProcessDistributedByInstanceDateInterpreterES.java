/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.date;

import static io.camunda.optimize.service.db.es.report.command.util.FilterLimitedAggregationUtilES.unwrapFilterLimitedAggregations;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasNames;

import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessReportDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.DateDistributedByValueDto;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancer;
import io.camunda.optimize.service.db.es.report.context.DateAggregationContextES;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.AbstractProcessDistributedByInterpreterES;
import io.camunda.optimize.service.db.es.report.service.DateAggregationServiceES;
import io.camunda.optimize.service.db.es.report.service.MinMaxStatsServiceES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

public abstract class AbstractProcessDistributedByInstanceDateInterpreterES
    extends AbstractProcessDistributedByInterpreterES {

  public abstract String getDateField();

  protected abstract DateAggregationServiceES getDateAggregationService();

  protected abstract ProcessQueryFilterEnhancer getQueryFilterEnhancer();

  protected abstract MinMaxStatsServiceES getMinMaxStatsService();

  @Override
  public List<AggregationBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final QueryBuilder baseQueryBuilder) {
    final AggregateByDateUnit unit = getDistributedByDateUnit(context.getReportData());
    final ProcessReportDistributedByDto<?> distributedByDto =
        context.getPlan().getDistributedBy().getDto();
    final MinMaxStatDto stats = getMinMaxStats(context, baseQueryBuilder);

    final DateAggregationContextES dateAggContext =
        DateAggregationContextES.builder()
            .aggregateByDateUnit(unit)
            .dateField(getDateField())
            .minMaxStats(stats)
            .extendBoundsToMinMaxStats(true)
            .timezone(context.getTimezone())
            .subAggregations(getViewInterpreter().createAggregations(context))
            .distributedByType(distributedByDto.getType())
            .processFilters(context.getReportData().getFilter())
            .processQueryFilterEnhancer(getQueryFilterEnhancer())
            .filterContext(context.getFilterContext())
            .build();

    return getDateAggregationService()
        .createProcessInstanceDateAggregation(dateAggContext)
        .map(Collections::singletonList)
        .orElse(getViewInterpreter().createAggregations(context));
  }

  @Override
  public List<DistributedByResult> retrieveResult(
      SearchResponse response,
      Aggregations aggregations,
      ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    if (aggregations == null) {
      // aggregations are null when there are no instances in the report
      return Collections.emptyList();
    }

    final Optional<Aggregations> unwrappedLimitedAggregations =
        unwrapFilterLimitedAggregations(aggregations);
    Map<String, Aggregations> keyToAggregationMap;
    if (unwrappedLimitedAggregations.isPresent()) {
      keyToAggregationMap =
          getDateAggregationService()
              .mapDateAggregationsToKeyAggregationMap(
                  unwrappedLimitedAggregations.get(), context.getTimezone());
    } else {
      return Collections.emptyList();
    }

    List<CompositeCommandResult.DistributedByResult> distributedByResults = new ArrayList<>();
    for (Map.Entry<String, Aggregations> keyToAggregationEntry : keyToAggregationMap.entrySet()) {
      final CompositeCommandResult.ViewResult viewResult =
          getViewInterpreter().retrieveResult(response, keyToAggregationEntry.getValue(), context);
      distributedByResults.add(
          createDistributedByResult(keyToAggregationEntry.getKey(), null, viewResult));
    }

    return distributedByResults;
  }

  private AggregateByDateUnit getDistributedByDateUnit(
      final ProcessReportDataDto processReportData) {
    return Optional.ofNullable(
            ((DateDistributedByValueDto) processReportData.getDistributedBy().getValue()))
        .map(DateDistributedByValueDto::getUnit)
        .orElse(AggregateByDateUnit.AUTOMATIC);
  }

  private MinMaxStatDto getMinMaxStats(
      final ExecutionContext<ProcessReportDataDto, ?> context,
      final QueryBuilder baseQueryBuilder) {
    return getMinMaxStatsService()
        .getMinMaxDateRange(
            context,
            baseQueryBuilder,
            getProcessInstanceIndexAliasNames(context.getReportData()),
            getDateField());
  }
}
