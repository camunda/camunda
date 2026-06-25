/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process;

import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_KEY_AGGREGATION;
import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.termAggregation;
import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.withSubaggregations;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_AGENT_PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Groups agentic token usage by process definition key and, when a limit-only pagination is
 * supplied, returns only the top N process definitions by the configured measure. The top N is
 * computed server-side (terms aggregation with {@code size=limit} ordered by the primary measure
 * plus a cardinality sub-aggregation for the total count) so it scales to large data sets. All
 * other behaviour is inherited from {@link ProcessGroupByProcessDefinitionKeyInterpreterOS}, which
 * is left untouched for every non-agentic process-definition-key report.
 */
@Component
@Conditional(OpenSearchCondition.class)
public class AgenticProcessDefinitionKeyGroupByInterpreterOS
    extends ProcessGroupByProcessDefinitionKeyInterpreterOS {

  // Must match the aggregation name used by the superclass so the inherited result handling finds
  // the buckets.
  private static final String PROCESS_DEFINITION_KEY_COUNT_AGGREGATION =
      "processDefinitionKeyCountAgg";

  public AgenticProcessDefinitionKeyGroupByInterpreterOS(
      final ConfigurationService configurationService,
      final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter,
      final ProcessViewInterpreterFacadeOS viewInterpreter) {
    super(configurationService, distributedByInterpreter, viewInterpreter);
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_AGENT_PROCESS_DEFINITION_KEY);
  }

  @Override
  public Map<String, Aggregation> createAggregation(
      final Query query,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Map<String, Aggregation> distributedByAggregations =
        getDistributedByInterpreter().createAggregations(context, query);
    final Optional<PaginationDto> topN = topNPagination(context);
    if (topN.isEmpty() || distributedByAggregations.isEmpty()) {
      // No limit requested: fall back to the full process-definition-key grouping.
      return super.createAggregation(query, context);
    }

    // Return only the top N process definitions by the configured measure, computed server-side so
    // we never fetch all buckets for large data sets.
    final TermsAggregation termsAggregation =
        termAggregation(
            PROCESS_DEFINITION_KEY,
            topN.get().getLimit(),
            Map.of(primaryMeasureAggregationName(distributedByAggregations), SortOrder.Desc));
    final Aggregation processDefinitionKeyAggregation =
        withSubaggregations(termsAggregation, distributedByAggregations);

    final Map<String, Aggregation> aggregations = new LinkedHashMap<>();
    aggregations.put(PROCESS_DEFINITION_KEY_AGGREGATION, processDefinitionKeyAggregation);
    aggregations.put(
        PROCESS_DEFINITION_KEY_COUNT_AGGREGATION,
        Aggregation.of(a -> a.cardinality(c -> c.field(PROCESS_DEFINITION_KEY))));
    return aggregations;
  }

  @Override
  protected void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse<RawResult> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    super.addQueryResult(compositeCommandResult, response, context);
    topNPagination(context)
        .ifPresent(
            pagination -> {
              final var countAggregation =
                  response.aggregations().get(PROCESS_DEFINITION_KEY_COUNT_AGGREGATION);
              if (countAggregation != null) {
                pagination.setTotal(countAggregation.cardinality().value());
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
      final Map<String, Aggregation> distributedByAggregations) {
    return distributedByAggregations.keySet().iterator().next();
  }
}
