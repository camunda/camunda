/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy.PROCESS_DISTRIBUTED_BY_AGENT_MODEL;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.interpreter.agentmodel.AgentModelAggregationES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Splits agent runs by {@code provider:model}, labelled with the model. A group only lists the
 * models that have runs in it.
 */
@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessDistributedByAgentModelInterpreterES
    extends AbstractProcessDistributedByInterpreterES {

  private final ConfigurationService configurationService;
  private final ProcessViewInterpreterFacadeES viewInterpreter;

  public ProcessDistributedByAgentModelInterpreterES(
      final ConfigurationService configurationService,
      final ProcessViewInterpreterFacadeES viewInterpreter) {
    this.configurationService = configurationService;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Set<ProcessDistributedBy> getSupportedDistributedBys() {
    return Set.of(PROCESS_DISTRIBUTED_BY_AGENT_MODEL);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final BoolQuery baseQuery) {
    return AgentModelAggregationES.createAggregation(
        configurationService.getElasticSearchConfiguration().getAggregationBucketLimit(),
        viewInterpreter.createAggregations(context));
  }

  @Override
  public List<DistributedByResult> retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return AgentModelAggregationES.modelBuckets(aggregations).stream()
        .map(
            bucket ->
                createDistributedByResult(
                    bucket.key(),
                    bucket.label(),
                    viewInterpreter.retrieveResult(response, bucket.aggregations(), context)))
        .collect(Collectors.toList());
  }

  @Override
  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return viewInterpreter;
  }
}
