/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.date;

import static io.camunda.optimize.service.db.es.report.interpreter.util.FilterLimitedAggregationUtilES.unwrapFilterLimitedAggregations;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasNames;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessReportDistributedByDto;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.report.context.DateAggregationContextES;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.AbstractProcessDistributedByInterpreterES;
import io.camunda.optimize.service.db.es.report.service.DateAggregationServiceES;
import io.camunda.optimize.service.db.es.report.service.MinMaxStatsServiceES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.interpreter.distributedby.process.date.ProcessDistributedByInstanceDateInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractProcessDistributedByInstanceDateInterpreterES
    extends AbstractProcessDistributedByInterpreterES
    implements ProcessDistributedByInstanceDateInterpreter {

  public abstract String getDateField();

  protected abstract DateAggregationServiceES getDateAggregationService();

  protected abstract ProcessQueryFilterEnhancerES getQueryFilterEnhancer();

  protected abstract MinMaxStatsServiceES getMinMaxStatsService();

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final BoolQuery baseQuery) {
    final AggregateByDateUnit unit = getDistributedByDateUnit(context.getReportData());
    final ProcessReportDistributedByDto<?> distributedByDto =
        context.getPlan().getDistributedBy().getDto();
    final MinMaxStatDto stats = getMinMaxStats(context, baseQuery);

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
        .orElse(getViewInterpreter().createAggregations(context));
  }

  @Override
  public List<CompositeCommandResult.DistributedByResult> retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    if (aggregations == null) {
      // aggregations are null when there are no instances in the report
      return Collections.emptyList();
    }

    final Optional<Map<String, Aggregate>> unwrappedLimitedAggregations =
        unwrapFilterLimitedAggregations(aggregations);
    final Map<String, Map<String, Aggregate>> keyToAggregationMap;
    if (unwrappedLimitedAggregations.isPresent()) {
      keyToAggregationMap =
          getDateAggregationService()
              .mapDateAggregationsToKeyAggregationMap(
                  unwrappedLimitedAggregations.get(), context.getTimezone());
    } else {
      return Collections.emptyList();
    }

    final List<CompositeCommandResult.DistributedByResult> distributedByResults = new ArrayList<>();
    for (final Map.Entry<String, Map<String, Aggregate>> keyToAggregationEntry :
        keyToAggregationMap.entrySet()) {
      final CompositeCommandResult.ViewResult viewResult =
          getViewInterpreter().retrieveResult(response, keyToAggregationEntry.getValue(), context);
      distributedByResults.add(
          createDistributedByResult(keyToAggregationEntry.getKey(), null, viewResult));
    }

    return distributedByResults;
  }

  private MinMaxStatDto getMinMaxStats(
      final ExecutionContext<ProcessReportDataDto, ?> context, final BoolQuery baseQuery) {
    return getMinMaxStatsService()
        .getMinMaxDateRange(
            context,
            Query.of(q -> q.bool(baseQuery)),
            getProcessInstanceIndexAliasNames(context.getReportData()),
            getDateField());
  }
}
