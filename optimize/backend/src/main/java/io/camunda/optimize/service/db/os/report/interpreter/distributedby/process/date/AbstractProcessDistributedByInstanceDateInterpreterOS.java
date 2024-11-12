/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.date;

import static io.camunda.optimize.service.db.os.report.interpreter.util.FilterLimitedAggregationUtilOS.unwrapFilterLimitedAggregations;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasNames;

import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessReportDistributedByDto;
import io.camunda.optimize.service.db.os.report.context.DateAggregationContextOS;
import io.camunda.optimize.service.db.os.report.filter.ProcessQueryFilterEnhancerOS;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.AbstractProcessDistributedByInterpreterOS;
import io.camunda.optimize.service.db.os.report.service.DateAggregationServiceOS;
import io.camunda.optimize.service.db.os.report.service.MinMaxStatsServiceOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.interpreter.distributedby.process.date.ProcessDistributedByInstanceDateInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.util.types.MapUtil;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;

public abstract class AbstractProcessDistributedByInstanceDateInterpreterOS
    extends AbstractProcessDistributedByInterpreterOS
    implements ProcessDistributedByInstanceDateInterpreter {

  public abstract String getDateField();

  protected abstract DateAggregationServiceOS getDateAggregationService();

  protected abstract ProcessQueryFilterEnhancerOS getQueryFilterEnhancer();

  protected abstract MinMaxStatsServiceOS getMinMaxStatsService();

  @Override
  public Map<String, Aggregation> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery) {
    final AggregateByDateUnit unit = getDistributedByDateUnit(context.getReportData());
    final ProcessReportDistributedByDto<?> distributedByDto =
        context.getPlan().getDistributedBy().getDto();
    final MinMaxStatDto stats = getMinMaxStats(context, baseQuery);

    final DateAggregationContextOS dateAggContext =
        DateAggregationContextOS.builder()
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
        .map(MapUtil::createFromPair)
        .orElse(getViewInterpreter().createAggregations(context));
  }

  @Override
  public List<DistributedByResult> retrieveResult(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    if (aggregations == null) {
      // aggregations are null when there are no instances in the report
      return List.of();
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

    return keyToAggregationMap.entrySet().stream()
        .map(
            keyToAggregationEntry ->
                createDistributedByResult(
                    keyToAggregationEntry.getKey(),
                    null,
                    getViewInterpreter()
                        .retrieveResult(response, keyToAggregationEntry.getValue(), context)))
        .toList();
  }

  private MinMaxStatDto getMinMaxStats(
      final ExecutionContext<ProcessReportDataDto, ?> context, final Query baseQuery) {
    return getMinMaxStatsService()
        .getMinMaxDateRange(
            context,
            baseQuery,
            getProcessInstanceIndexAliasNames(context.getReportData()),
            getDateField());
  }
}
