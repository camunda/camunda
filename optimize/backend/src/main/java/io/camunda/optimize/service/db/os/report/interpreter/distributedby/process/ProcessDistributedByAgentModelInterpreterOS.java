/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.distributedby.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy.PROCESS_DISTRIBUTED_BY_AGENT_MODEL;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.agentmodel.AgentModelAggregationOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Splits agent runs by {@code provider:model}, labelled with the model. A group only lists the
 * models that have runs in it.
 */
@Component
@Conditional(OpenSearchCondition.class)
public class ProcessDistributedByAgentModelInterpreterOS
    extends AbstractProcessDistributedByInterpreterOS {

  private final ConfigurationService configurationService;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;

  public ProcessDistributedByAgentModelInterpreterOS(
      final ConfigurationService configurationService,
      final ProcessViewInterpreterFacadeOS viewInterpreter) {
    this.configurationService = configurationService;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Set<ProcessDistributedBy> getSupportedDistributedBys() {
    return Set.of(PROCESS_DISTRIBUTED_BY_AGENT_MODEL);
  }

  @Override
  public Map<String, Aggregation> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery) {
    return AgentModelAggregationOS.createAggregation(
        configurationService.getOpenSearchConfiguration().getAggregationBucketLimit(),
        viewInterpreter.createAggregations(context));
  }

  @Override
  public List<DistributedByResult> retrieveResult(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return AgentModelAggregationOS.modelBuckets(aggregations).stream()
        .map(
            bucket ->
                createDistributedByResult(
                    bucket.key(),
                    bucket.label(),
                    viewInterpreter.retrieveResult(response, bucket.aggregations(), context)))
        .collect(Collectors.toList());
  }

  @Override
  public ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return viewInterpreter;
  }
}
