/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.flownode;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_FLOW_NODE_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.command.util.DurationScriptUtilES;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.service.DurationAggregationServiceES;
import io.camunda.optimize.service.db.es.report.service.MinMaxStatsServiceES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class ProcessGroupByFlowNodeDurationInterpreterES
    extends AbstractGroupByFlowNodeInterpreterES {
  private final MinMaxStatsServiceES minMaxStatsService;
  @Getter private final DefinitionService definitionService;
  @Getter final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  private final DurationAggregationServiceES durationAggregationService;
  @Getter final ProcessViewInterpreterFacadeES viewInterpreter;

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_FLOW_NODE_DURATION);
  }

  @Override
  public List<AggregationBuilder> createAggregation(
      final SearchSourceBuilder searchSourceBuilder,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return durationAggregationService
        .createLimitedGroupByScriptedEventDurationAggregation(
            searchSourceBuilder, context, getDurationScript())
        .map(durationAggregation -> createFilteredFlowNodeAggregation(context, durationAggregation))
        .map(Collections::singletonList)
        .orElse(Collections.emptyList());
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    compositeCommandResult.setGroupByKeyOfNumericType(true);
    compositeCommandResult.setDistributedByKeyOfNumericType(
        distributedByInterpreter.isKeyOfNumericType(context));
    getFilteredFlowNodesAggregation(response)
        .ifPresent(
            filteredFlowNodes -> {
              final List<CompositeCommandResult.GroupByResult> durationHistogramData =
                  durationAggregationService.mapGroupByDurationResults(
                      response, filteredFlowNodes.getAggregations(), context);

              compositeCommandResult.setGroups(durationHistogramData);
            });
  }

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final BoolQueryBuilder baseQuery) {
    return Optional.of(retrieveMinMaxDurationStats(context, baseQuery));
  }

  private MinMaxStatDto retrieveMinMaxDurationStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final QueryBuilder baseQuery) {
    return minMaxStatsService.getScriptedMinMaxStats(
        baseQuery, getIndexNames(context), FLOW_NODE_INSTANCES, getDurationScript());
  }

  private Script getDurationScript() {
    return DurationScriptUtilES.getDurationScript(
        LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
        FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION,
        FLOW_NODE_INSTANCES + "." + FLOW_NODE_START_DATE);
  }
}
