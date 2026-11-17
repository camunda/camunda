/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.process;

import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.filterAggregation;
import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.withSubaggregations;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.stringTerms;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_INSTANCE_AUTOMATION_RATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_BUSINESS_RULE_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_MANUAL_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_SCRIPT_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_SEND_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_SERVICE_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_USER_TASK;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregate;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregation;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * OpenSearch twin of {@code ProcessViewAutomationRateInterpreterES}. Computes automation rate =
 * automated / (automated + human) * 100 via a nested aggregation over {@code flowNodeInstances}
 * with two sibling filter sub-aggregations (automated + human task types). Structural elements are
 * excluded by matching neither filter.
 */
@Component
@Conditional(OpenSearchCondition.class)
public class ProcessViewAutomationRateInterpreterOS implements ProcessViewInterpreterOS {

  public static final String AUTOMATION_NESTED_AGGREGATION = "automationRate_nested";
  public static final String AUTOMATED_TASKS_AGGREGATION = "automationRate_automated";
  public static final String HUMAN_TASKS_AGGREGATION = "automationRate_human";

  private static final List<String> AUTOMATED_TASK_TYPES =
      List.of(
          FLOW_NODE_TYPE_SERVICE_TASK,
          FLOW_NODE_TYPE_BUSINESS_RULE_TASK,
          FLOW_NODE_TYPE_SCRIPT_TASK,
          FLOW_NODE_TYPE_SEND_TASK);
  private static final List<String> HUMAN_TASK_TYPES =
      List.of(FLOW_NODE_TYPE_USER_TASK, FLOW_NODE_TYPE_MANUAL_TASK);

  @Override
  public Set<ProcessView> getSupportedViews() {
    return Set.of(PROCESS_VIEW_INSTANCE_AUTOMATION_RATE);
  }

  @Override
  public Map<String, Aggregation> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final NestedAggregation nested = NestedAggregation.of(n -> n.path(FLOW_NODE_INSTANCES));
    return Map.of(
        AUTOMATION_NESTED_AGGREGATION,
        withSubaggregations(
            nested,
            Map.of(
                AUTOMATED_TASKS_AGGREGATION,
                filterAggregation(
                    stringTerms(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TYPE, AUTOMATED_TASK_TYPES)),
                HUMAN_TASKS_AGGREGATION,
                filterAggregation(
                    stringTerms(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TYPE, HUMAN_TASK_TYPES)))));
  }

  @Override
  public ViewResult retrieveResult(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final NestedAggregate nested = aggregations.get(AUTOMATION_NESTED_AGGREGATION).nested();
    final long automated =
        nested.aggregations().get(AUTOMATED_TASKS_AGGREGATION).filter().docCount();
    final long human = nested.aggregations().get(HUMAN_TASKS_AGGREGATION).filter().docCount();
    final long total = automated + human;
    // No automation-eligible tasks (only events/gateways/sub-process containers, or no
    // completed instances in range). Return null so the frontend can render an empty state
    // (—) instead of a misleading 0% which would visually equal a fully-manual process.
    final Double rate = total == 0 ? null : ((double) automated / total) * 100.0;
    return createViewResult(rate);
  }

  @Override
  public ViewResult createEmptyResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return createViewResult(null);
  }

  private ViewResult createViewResult(final Double value) {
    return ViewResult.builder()
        .viewMeasure(CompositeCommandResult.ViewMeasure.builder().value(value).build())
        .build();
  }
}
