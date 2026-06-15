/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessGroupByProcessDefinitionKeyInterpreterES
    extends AbstractProcessGroupByInterpreterES {

  private static final String PROCESS_DEFINITION_KEY_AGGREGATION = "processDefinitionKeyAgg";
  private static final String PROCESS_DEFINITION_KEY_COUNT_AGGREGATION =
      "processDefinitionKeyCountAgg";

  private final ConfigurationService configurationService;
  private final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  private final ProcessViewInterpreterFacadeES viewInterpreter;

  public ProcessGroupByProcessDefinitionKeyInterpreterES(
      final ConfigurationService configurationService,
      final ProcessDistributedByInterpreterFacadeES distributedByInterpreter,
      final ProcessViewInterpreterFacadeES viewInterpreter) {
    this.configurationService = configurationService;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_PROCESS_DEFINITION_KEY);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final BoolQuery boolQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Map<String, Aggregation.Builder.ContainerBuilder> distributedByAggregations =
        distributedByInterpreter.createAggregations(context, boolQuery);
    final Optional<PaginationDto> topN = topNPagination(context);

    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder()
            .terms(
                terms -> {
                  terms.field(PROCESS_DEFINITION_KEY);
                  if (topN.isPresent() && !distributedByAggregations.isEmpty()) {
                    // Return only the top N process definitions by the configured measure,
                    // computed server-side so we never fetch all buckets for large data sets.
                    terms
                        .size(topN.get().getLimit())
                        .order(
                            NamedValue.of(
                                primaryMeasureAggregationName(distributedByAggregations),
                                SortOrder.Desc));
                  } else {
                    terms
                        .size(
                            configurationService
                                .getElasticSearchConfiguration()
                                .getAggregationBucketLimit())
                        .order(NamedValue.of("_key", SortOrder.Asc));
                  }
                  return terms;
                });
    distributedByAggregations.forEach((key, value) -> builder.aggregations(key, value.build()));

    final Map<String, Aggregation.Builder.ContainerBuilder> aggregations = new LinkedHashMap<>();
    aggregations.put(PROCESS_DEFINITION_KEY_AGGREGATION, builder);
    if (topN.isPresent()) {
      aggregations.put(
          PROCESS_DEFINITION_KEY_COUNT_AGGREGATION,
          new Aggregation.Builder().cardinality(c -> c.field(PROCESS_DEFINITION_KEY)));
    }
    return aggregations;
  }

  @Override
  protected void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final ResponseBody<?> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final StringTermsAggregate processDefinitionKeyAggregation =
        response.aggregations().get(PROCESS_DEFINITION_KEY_AGGREGATION).sterms();
    final List<GroupByResult> groupedData = new ArrayList<>();
    for (final StringTermsBucket processDefinitionKeyBucket :
        processDefinitionKeyAggregation.buckets().array()) {
      final String processDefinitionKey = processDefinitionKeyBucket.key().stringValue();
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

  @Override
  public ProcessDistributedByInterpreterFacadeES getDistributedByInterpreter() {
    return distributedByInterpreter;
  }

  @Override
  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return viewInterpreter;
  }
}
