/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process;

import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_KEY_AGGREGATION;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_AGENT_PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import co.elastic.clients.util.NamedValue;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Groups agentic token usage by process definition key and, when a limit-only pagination is
 * supplied, returns only the top N process definitions by the configured measure. The top N is
 * computed server-side (terms aggregation with {@code size=limit} ordered by the primary measure
 * plus a cardinality sub-aggregation for the total count) so it scales to large data sets. All
 * other behaviour is inherited from {@link ProcessGroupByProcessDefinitionKeyInterpreterES}, which
 * is left untouched for every non-agentic process-definition-key report.
 */
@Component
@Conditional(ElasticSearchCondition.class)
public class AgenticProcessDefinitionKeyGroupByInterpreterES
    extends ProcessGroupByProcessDefinitionKeyInterpreterES {

  // Must match the aggregation name used by the superclass so the inherited result handling finds
  // the buckets.
  private static final String PROCESS_DEFINITION_KEY_COUNT_AGGREGATION =
      "processDefinitionKeyCountAgg";

  public AgenticProcessDefinitionKeyGroupByInterpreterES(
      final ConfigurationService configurationService,
      final ProcessDistributedByInterpreterFacadeES distributedByInterpreter,
      final ProcessViewInterpreterFacadeES viewInterpreter) {
    super(configurationService, distributedByInterpreter, viewInterpreter);
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_AGENT_PROCESS_DEFINITION_KEY);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final BoolQuery boolQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Map<String, Aggregation.Builder.ContainerBuilder> distributedByAggregations =
        getDistributedByInterpreter().createAggregations(context, boolQuery);
    final Optional<PaginationDto> topN = topNPagination(context);
    if (topN.isEmpty() || distributedByAggregations.isEmpty()) {
      // No limit requested: fall back to the full process-definition-key grouping.
      return super.createAggregation(boolQuery, context);
    }

    // Return only the top N process definitions by the configured measure, computed server-side so
    // we never fetch all buckets for large data sets.
    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder()
            .terms(
                terms ->
                    terms
                        .field(PROCESS_DEFINITION_KEY)
                        .size(topN.get().getLimit())
                        .order(
                            NamedValue.of(
                                primaryMeasureAggregationName(distributedByAggregations),
                                SortOrder.Desc)));
    distributedByAggregations.forEach((key, value) -> builder.aggregations(key, value.build()));

    final Map<String, Aggregation.Builder.ContainerBuilder> aggregations = new LinkedHashMap<>();
    aggregations.put(PROCESS_DEFINITION_KEY_AGGREGATION, builder);
    aggregations.put(
        PROCESS_DEFINITION_KEY_COUNT_AGGREGATION,
        new Aggregation.Builder().cardinality(c -> c.field(PROCESS_DEFINITION_KEY)));
    return aggregations;
  }

  @Override
  protected void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final ResponseBody<?> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    super.addQueryResult(compositeCommandResult, response, context);
    topNPagination(context)
        .ifPresent(
            pagination -> {
              final var countAggregation =
                  response.aggregations().get(PROCESS_DEFINITION_KEY_COUNT_AGGREGATION);
              if (countAggregation != null) {
                pagination.setTotal((long) countAggregation.cardinality().value());
              }
            });
  }

  private Optional<PaginationDto> topNPagination(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return context.getPagination().filter(pagination -> pagination.getLimit() != null);
  }

  // The distributed-by facade emits one sub-aggregation per measure; the first is the primary
  // measure used to rank the top-N buckets.
  private static String primaryMeasureAggregationName(
      final Map<String, Aggregation.Builder.ContainerBuilder> distributedByAggregations) {
    return distributedByAggregations.keySet().iterator().next();
  }
}
