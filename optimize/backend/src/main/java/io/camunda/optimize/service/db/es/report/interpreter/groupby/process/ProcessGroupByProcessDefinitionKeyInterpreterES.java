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
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessGroupByProcessDefinitionKeyInterpreterES
    extends AbstractProcessGroupByInterpreterES {

  private static final String PROCESS_DEFINITION_KEY_AGGREGATION = "processDefinitionKeyAgg";

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
    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder()
            .terms(
                terms ->
                    terms
                        .size(
                            configurationService
                                .getElasticSearchConfiguration()
                                .getAggregationBucketLimit())
                        .field(PROCESS_DEFINITION_KEY)
                        .order(NamedValue.of("_key", SortOrder.Asc)));
    distributedByInterpreter
        .createAggregations(context, boolQuery)
        .forEach((key, value) -> builder.aggregations(key, value.build()));
    return Map.of(PROCESS_DEFINITION_KEY_AGGREGATION, builder);
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
