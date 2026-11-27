/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_AGENT_MODEL;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.agentmodel.AgentModelAggregationOS;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/** Groups agent runs by {@code provider:model}, labelled with the model. */
@Component
@Conditional(OpenSearchCondition.class)
public class ProcessGroupByAgentModelInterpreterOS extends AbstractProcessGroupByInterpreterOS {

  private final ConfigurationService configurationService;
  private final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;

  public ProcessGroupByAgentModelInterpreterOS(
      final ConfigurationService configurationService,
      final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter,
      final ProcessViewInterpreterFacadeOS viewInterpreter) {
    this.configurationService = configurationService;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_AGENT_MODEL);
  }

  @Override
  public Map<String, Aggregation> createAggregation(
      final Query query,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return AgentModelAggregationOS.createAggregation(
        configurationService.getOpenSearchConfiguration().getAggregationBucketLimit(),
        distributedByInterpreter.createAggregations(context, query));
  }

  @Override
  protected void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse<RawResult> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    compositeCommandResult.setGroups(
        AgentModelAggregationOS.modelBuckets(response.aggregations()).stream()
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
  public ProcessDistributedByInterpreterFacadeOS getDistributedByInterpreter() {
    return distributedByInterpreter;
  }

  @Override
  public ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return viewInterpreter;
  }
}
