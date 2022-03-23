/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.process;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessReportDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.DateDistributedByValueDto;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.MinMaxStatsService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.service.DateAggregationService;
import org.camunda.optimize.service.es.report.command.util.DateAggregationContext;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.unwrapFilterLimitedAggregations;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasNames;

@RequiredArgsConstructor
public abstract class AbstractProcessDistributedByInstanceDate extends ProcessDistributedByPart {

  protected final DateAggregationService dateAggregationService;
  protected final MinMaxStatsService minMaxStatsService;
  protected final ProcessQueryFilterEnhancer queryFilterEnhancer;

  @Override
  public List<AggregationBuilder> createAggregations(final ExecutionContext<ProcessReportDataDto> context) {
    final AggregateByDateUnit unit = getDistributedByDateUnit(context.getReportData());

    final MinMaxStatDto stats = getMinMaxStats(context);

    final DateAggregationContext dateAggContext = DateAggregationContext.builder()
      .aggregateByDateUnit(unit)
      .dateField(getDateField())
      .minMaxStats(stats)
      .extendBoundsToMinMaxStats(true)
      .timezone(context.getTimezone())
      .subAggregations(viewPart.createAggregations(context))
      .distributedByType(getDistributedBy().getType())
      .processFilters(context.getReportData().getFilter())
      .processQueryFilterEnhancer(queryFilterEnhancer)
      .filterContext(context.getFilterContext())
      .build();

    return dateAggregationService.createProcessInstanceDateAggregation(dateAggContext)
      .map(Collections::singletonList)
      .orElse(viewPart.createAggregations(context));
  }

  @Override
  public List<CompositeCommandResult.DistributedByResult> retrieveResult(final SearchResponse response,
                                                                         final Aggregations aggregations,
                                                                         final ExecutionContext<ProcessReportDataDto> context) {
    if (aggregations == null) {
      // aggregations are null when there are no instances in the report
      return Collections.emptyList();
    }

    final Optional<Aggregations> unwrappedLimitedAggregations = unwrapFilterLimitedAggregations(aggregations);
    Map<String, Aggregations> keyToAggregationMap;
    if (unwrappedLimitedAggregations.isPresent()) {
      keyToAggregationMap = dateAggregationService.mapDateAggregationsToKeyAggregationMap(
        unwrappedLimitedAggregations.get(),
        context.getTimezone()
      );
    } else {
      return Collections.emptyList();
    }

    List<CompositeCommandResult.DistributedByResult> distributedByResults = new ArrayList<>();
    for (Map.Entry<String, Aggregations> keyToAggregationEntry : keyToAggregationMap.entrySet()) {
      final CompositeCommandResult.ViewResult viewResult = viewPart.retrieveResult(
        response,
        keyToAggregationEntry.getValue(),
        context
      );
      distributedByResults.add(createDistributedByResult(keyToAggregationEntry.getKey(), null, viewResult));
    }

    return distributedByResults;
  }

  @Override
  protected void addAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setDistributedBy(getDistributedBy());
  }

  protected abstract ProcessReportDistributedByDto<DateDistributedByValueDto> getDistributedBy();

  public abstract String getDateField();

  private AggregateByDateUnit getDistributedByDateUnit(final ProcessReportDataDto processReportData) {
    return Optional.ofNullable(((DateDistributedByValueDto) processReportData
      .getDistributedBy()
      .getValue()))
      .map(DateDistributedByValueDto::getUnit)
      .orElse(AggregateByDateUnit.AUTOMATIC);
  }

  private MinMaxStatDto getMinMaxStats(final ExecutionContext<ProcessReportDataDto> context) {
    return minMaxStatsService.getMinMaxDateRange(
      context,
      context.getDistributedByMinMaxBaseQuery(),
      getProcessInstanceIndexAliasNames(context.getReportData()),
      getDateField()
    );
  }
}
