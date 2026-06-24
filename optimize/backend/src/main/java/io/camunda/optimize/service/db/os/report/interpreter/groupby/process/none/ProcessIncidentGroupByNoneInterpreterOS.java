/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.optimize.service.db.os.report.interpreter.groupby.process.none;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENTS;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.os.report.filter.util.IncidentFilterQueryUtilOS;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.process.AbstractProcessGroupByInterpreterOS;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.process.ProcessGroupByInterpreterOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.FilterAggregate;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregate;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessIncidentGroupByNoneInterpreterOS extends AbstractProcessGroupByInterpreterOS
    implements ProcessGroupByInterpreterOS {

  private static final String NESTED_INCIDENT_AGGREGATION = "incidentAggregation";
  private static final String FILTERED_INCIDENT_AGGREGATION = "filteredIncidentAggregation";

  private final ProcessViewInterpreterFacadeOS viewInterpreter;
  private final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  private final DefinitionService definitionService;

  public ProcessIncidentGroupByNoneInterpreterOS(
      final ProcessViewInterpreterFacadeOS viewInterpreter,
      final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter,
      final DefinitionService definitionService) {
    this.viewInterpreter = viewInterpreter;
    this.distributedByInterpreter = distributedByInterpreter;
    this.definitionService = definitionService;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(ProcessGroupBy.PROCESS_INCIDENT_GROUP_BY_NONE);
  }

  @Override
  public Map<String, Aggregation> createAggregation(
      final Query query,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Map<String, Aggregation> distributedByAggs =
        getDistributedByInterpreter().createAggregations(context, query);

    final Aggregation filteredIncidentAggregation =
        new Aggregation.Builder()
            .filter(
                IncidentFilterQueryUtilOS.createIncidentAggregationFilterQuery(
                    context.getReportData(), definitionService))
            .aggregations(distributedByAggs)
            .build();

    final Aggregation nestedAggregation =
        new Aggregation.Builder()
            .nested(n -> n.path(INCIDENTS))
            .aggregations(FILTERED_INCIDENT_AGGREGATION, filteredIncidentAggregation)
            .build();

    return Map.of(NESTED_INCIDENT_AGGREGATION, nestedAggregation);
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse<RawResult> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    getNestedIncidentsAggregation(response)
        .ifPresent(
            nestedIncidents -> {
              final List<CompositeCommandResult.DistributedByResult> distributions =
                  getDistributedByInterpreter()
                      .retrieveResult(response, nestedIncidents.aggregations(), context);
              final CompositeCommandResult.GroupByResult groupByResult =
                  CompositeCommandResult.GroupByResult.createGroupByNone(distributions);
              compositeCommandResult.setGroup(groupByResult);
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

  private Optional<FilterAggregate> getNestedIncidentsAggregation(
      final SearchResponse<RawResult> response) {
    return getFilteredIncidentsAggregation(response)
        .map(NestedAggregate::aggregations)
        .map(aggs -> aggs.get(FILTERED_INCIDENT_AGGREGATION).filter());
  }

  private Optional<NestedAggregate> getFilteredIncidentsAggregation(
      final SearchResponse<RawResult> response) {
    return Optional.ofNullable(response.aggregations())
        .map(aggs -> aggs.get(NESTED_INCIDENT_AGGREGATION).nested());
  }
}
