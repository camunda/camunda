/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.none;

import static io.camunda.optimize.service.db.es.filter.util.IncidentFilterQueryUtilES.createIncidentAggregationFilter;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENTS;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.FilterAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.NestedAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.SingleBucketAggregateBase;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.AbstractProcessGroupByInterpreterES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.ProcessGroupByInterpreterES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessIncidentGroupByNoneInterpreterES extends AbstractProcessGroupByInterpreterES
    implements ProcessGroupByInterpreterES {

  private static final String NESTED_INCIDENT_AGGREGATION = "incidentAggregation";
  private static final String FILTERED_INCIDENT_AGGREGATION = "filteredIncidentAggregation";

  private final ProcessViewInterpreterFacadeES viewInterpreter;
  private final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  private final DefinitionService definitionService;

  public ProcessIncidentGroupByNoneInterpreterES(
      final ProcessViewInterpreterFacadeES viewInterpreter,
      final ProcessDistributedByInterpreterFacadeES distributedByInterpreter,
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
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final BoolQuery boolQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder().nested(n -> n.path(INCIDENTS));
    builder.aggregations(
        FILTERED_INCIDENT_AGGREGATION,
        Aggregation.of(
            a -> {
              final Aggregation.Builder.ContainerBuilder filter =
                  a.filter(
                      f ->
                          f.bool(
                              createIncidentAggregationFilter(
                                      context.getReportData(), definitionService)
                                  .build()));
              getDistributedByInterpreter()
                  .createAggregations(context, boolQuery)
                  .forEach((k, v) -> filter.aggregations(k, v.build()));
              return filter;
            }));
    return Map.of(NESTED_INCIDENT_AGGREGATION, builder);
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final ResponseBody<?> response,
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

  private Optional<FilterAggregate> getNestedIncidentsAggregation(final ResponseBody<?> response) {
    return getFilteredIncidentsAggregation(response)
        .map(SingleBucketAggregateBase::aggregations)
        .map(aggs -> aggs.get(FILTERED_INCIDENT_AGGREGATION).filter());
  }

  private Optional<NestedAggregate> getFilteredIncidentsAggregation(
      final ResponseBody<?> response) {
    return Optional.ofNullable(response.aggregations())
        .map(aggs -> aggs.get(NESTED_INCIDENT_AGGREGATION).nested());
  }

  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return this.viewInterpreter;
  }

  public ProcessDistributedByInterpreterFacadeES getDistributedByInterpreter() {
    return this.distributedByInterpreter;
  }
}
