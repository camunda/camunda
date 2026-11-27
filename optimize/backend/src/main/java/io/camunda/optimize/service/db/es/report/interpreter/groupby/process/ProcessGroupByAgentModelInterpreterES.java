/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_AGENT_MODEL;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.interpreter.agentmodel.AgentModelAggregationES;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/** Groups agent runs by {@code provider:model}, labelled with the model. */
@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessGroupByAgentModelInterpreterES extends AbstractProcessGroupByInterpreterES {

  private final ConfigurationService configurationService;
  private final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  private final ProcessViewInterpreterFacadeES viewInterpreter;

  public ProcessGroupByAgentModelInterpreterES(
      final ConfigurationService configurationService,
      final ProcessDistributedByInterpreterFacadeES distributedByInterpreter,
      final ProcessViewInterpreterFacadeES viewInterpreter) {
    this.configurationService = configurationService;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_AGENT_MODEL);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final BoolQuery boolQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return AgentModelAggregationES.createAggregation(
        configurationService.getElasticSearchConfiguration().getAggregationBucketLimit(),
        distributedByInterpreter.createAggregations(context, boolQuery));
  }

  @Override
  protected void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final ResponseBody<?> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    compositeCommandResult.setGroups(
        AgentModelAggregationES.modelBuckets(response.aggregations()).stream()
            .map(
                bucket ->
                    GroupByResult.createGroupByResult(
                        bucket.key(),
                        bucket.label(),
                        distributedByInterpreter.retrieveResult(
                            response, bucket.aggregations(), context)))
            .collect(Collectors.toList()));
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
