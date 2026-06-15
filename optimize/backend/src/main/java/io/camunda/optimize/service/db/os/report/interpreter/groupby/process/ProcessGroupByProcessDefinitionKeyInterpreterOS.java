/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process;

import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.termAggregation;
import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.withSubaggregations;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessGroupByProcessDefinitionKeyInterpreterOS
    extends AbstractProcessGroupByInterpreterOS {

  private static final String PROCESS_DEFINITION_KEY_AGGREGATION = "processDefinitionKeyAgg";
  private static final String PROCESS_DEFINITION_KEY_COUNT_AGGREGATION =
      "processDefinitionKeyCountAgg";

  private final ConfigurationService configurationService;
  private final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;

  public ProcessGroupByProcessDefinitionKeyInterpreterOS(
      final ConfigurationService configurationService,
      final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter,
      final ProcessViewInterpreterFacadeOS viewInterpreter) {
    this.configurationService = configurationService;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_PROCESS_DEFINITION_KEY);
  }

  @Override
  public Map<String, Aggregation> createAggregation(
      final Query query,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Map<String, Aggregation> distributedByAggregations =
        distributedByInterpreter.createAggregations(context, query);
    final Optional<PaginationDto> topN = topNPagination(context);

    final TermsAggregation termsAggregation;
    if (topN.isPresent() && !distributedByAggregations.isEmpty()) {
      // Return only the top N process definitions by the configured measure, computed
      // server-side so we never fetch all buckets for large data sets.
      termsAggregation =
          termAggregation(
              PROCESS_DEFINITION_KEY,
              topN.get().getLimit(),
              Map.of(primaryMeasureAggregationName(distributedByAggregations), SortOrder.Desc));
    } else {
      termsAggregation =
          termAggregation(
              PROCESS_DEFINITION_KEY,
              configurationService.getOpenSearchConfiguration().getAggregationBucketLimit(),
              Map.of("_key", SortOrder.Asc));
    }
    final Aggregation processDefinitionKeyAggregation =
        withSubaggregations(termsAggregation, distributedByAggregations);

    final Map<String, Aggregation> aggregations = new LinkedHashMap<>();
    aggregations.put(PROCESS_DEFINITION_KEY_AGGREGATION, processDefinitionKeyAggregation);
    if (topN.isPresent()) {
      aggregations.put(
          PROCESS_DEFINITION_KEY_COUNT_AGGREGATION,
          Aggregation.of(a -> a.cardinality(c -> c.field(PROCESS_DEFINITION_KEY))));
    }
    return aggregations;
  }

  @Override
  protected void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse<RawResult> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final StringTermsAggregate processDefinitionKeyAggregation =
        response.aggregations().get(PROCESS_DEFINITION_KEY_AGGREGATION).sterms();
    final List<GroupByResult> groupedData = new ArrayList<>();
    for (final StringTermsBucket processDefinitionKeyBucket :
        processDefinitionKeyAggregation.buckets().array()) {
      final String processDefinitionKey = processDefinitionKeyBucket.key();
      final List<CompositeCommandResult.DistributedByResult> distributedByResult =
          distributedByInterpreter.retrieveResult(
              response, processDefinitionKeyBucket.aggregations(), context);
      groupedData.add(GroupByResult.createGroupByResult(processDefinitionKey, distributedByResult));
    }
    compositeCommandResult.setGroups(groupedData);
    compositeCommandResult.setDistributedByKeyOfNumericType(
        distributedByInterpreter.isKeyOfNumericType(context));

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

  @Override
  public ProcessDistributedByInterpreterFacadeOS getDistributedByInterpreter() {
    return distributedByInterpreter;
  }

  @Override
  public ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return viewInterpreter;
  }
}
