/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.none;

import static io.camunda.optimize.service.db.es.filter.util.IncidentFilterQueryUtil.createIncidentAggregationFilter;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENTS;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

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
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class ProcessIncidentGroupByNoneInterpreterES extends AbstractProcessGroupByInterpreterES
    implements ProcessGroupByInterpreterES {
  private static final String NESTED_INCIDENT_AGGREGATION = "incidentAggregation";
  private static final String FILTERED_INCIDENT_AGGREGATION = "filteredIncidentAggregation";

  @Getter private final ProcessViewInterpreterFacadeES viewInterpreter;
  @Getter private final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  private final DefinitionService definitionService;

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(ProcessGroupBy.PROCESS_INCIDENT_GROUP_BY_NONE);
  }

  @Override
  public List<AggregationBuilder> createAggregation(
      final SearchSourceBuilder searchSourceBuilder,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final FilterAggregationBuilder filteredIncidentsAggregation =
        filter(
            FILTERED_INCIDENT_AGGREGATION,
            createIncidentAggregationFilter(context.getReportData(), definitionService));
    distributedByInterpreter
        .createAggregations(context, searchSourceBuilder.query())
        .forEach(filteredIncidentsAggregation::subAggregation);
    return Stream.of(
            nested(NESTED_INCIDENT_AGGREGATION, INCIDENTS)
                .subAggregation(filteredIncidentsAggregation))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    getNestedIncidentsAggregation(response)
        .ifPresent(
            nestedIncidents -> {
              final List<DistributedByResult> distributions =
                  distributedByInterpreter.retrieveResult(
                      response, nestedIncidents.getAggregations(), context);
              GroupByResult groupByResult = GroupByResult.createGroupByNone(distributions);
              compositeCommandResult.setGroup(groupByResult);
            });
  }

  private Optional<Filter> getNestedIncidentsAggregation(final SearchResponse response) {
    return getFilteredIncidentsAggregation(response)
        .map(SingleBucketAggregation::getAggregations)
        .map(aggs -> aggs.get(FILTERED_INCIDENT_AGGREGATION));
  }

  private Optional<Nested> getFilteredIncidentsAggregation(final SearchResponse response) {
    return Optional.ofNullable(response.getAggregations())
        .map(aggs -> aggs.get(NESTED_INCIDENT_AGGREGATION));
  }
}
