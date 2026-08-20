/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process.flownode;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_FLOW_NODE_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.util.DurationScriptUtilOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.service.DurationAggregationServiceOS;
import io.camunda.optimize.service.db.os.report.service.MinMaxStatsServiceOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import io.camunda.optimize.util.types.MapUtil;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessGroupByFlowNodeDurationInterpreterOS
    extends AbstractGroupByFlowNodeInterpreterOS {
  private final MinMaxStatsServiceOS minMaxStatsService;
  private final DefinitionService definitionService;
  private final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  private final DurationAggregationServiceOS durationAggregationService;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;

  public ProcessGroupByFlowNodeDurationInterpreterOS(
      final MinMaxStatsServiceOS minMaxStatsService,
      final DefinitionService definitionService,
      final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter,
      final DurationAggregationServiceOS durationAggregationService,
      final ProcessViewInterpreterFacadeOS viewInterpreter) {
    super();
    this.minMaxStatsService = minMaxStatsService;
    this.definitionService = definitionService;
    this.distributedByInterpreter = distributedByInterpreter;
    this.durationAggregationService = durationAggregationService;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public DefinitionService getDefinitionService() {
    return definitionService;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_FLOW_NODE_DURATION);
  }

  @Override
  public Map<String, Aggregation> createAggregation(
      final Query query,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return durationAggregationService
        .createLimitedGroupByScriptedEventDurationAggregation(query, context, getDurationScript())
        .map(MapUtil::createFromPair)
        .map(durationAggregation -> createFilteredFlowNodeAggregation(context, durationAggregation))
        .orElse(Map.of());
  }

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery) {
    return Optional.of(retrieveMinMaxDurationStats(context, baseQuery));
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse<RawResult> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    compositeCommandResult.setGroupByKeyOfNumericType(true);
    compositeCommandResult.setDistributedByKeyOfNumericType(
        distributedByInterpreter.isKeyOfNumericType(context));
    getFilteredFlowNodesAggregation(response)
        .ifPresent(
            filteredFlowNodes -> {
              final List<CompositeCommandResult.GroupByResult> durationHistogramData =
                  durationAggregationService.mapGroupByDurationResults(
                      response, filteredFlowNodes.aggregations(), context);

              compositeCommandResult.setGroups(durationHistogramData);
            });
  }

  @Override
  public ProcessDistributedByInterpreterFacadeOS getDistributedByInterpreter() {
    return distributedByInterpreter;
  }

  @Override
  public ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return viewInterpreter;
  }

  private MinMaxStatDto retrieveMinMaxDurationStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery) {
    return minMaxStatsService.getScriptedMinMaxStats(
        baseQuery, getIndexNames(context), FLOW_NODE_INSTANCES, getDurationScript());
  }

  private Script getDurationScript() {
    return DurationScriptUtilOS.getDurationScript(
        LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
        FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION,
        FLOW_NODE_INSTANCES + "." + FLOW_NODE_START_DATE);
  }
}
