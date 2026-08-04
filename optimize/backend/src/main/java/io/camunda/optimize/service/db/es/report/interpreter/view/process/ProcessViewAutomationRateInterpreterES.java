/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_INSTANCE_AUTOMATION_RATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_BUSINESS_RULE_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_MANUAL_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_SCRIPT_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_SEND_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_SERVICE_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_USER_TASK;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.NestedAggregate;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Computes automation rate = automated / (automated + human) * 100 across the flow-node instances
 * of the buckets selected by the group-by. Emits one nested aggregation on the {@code
 * flowNodeInstances} array with two filter sub-aggregations — one counting automated task
 * flow-nodes (serviceTask, businessRuleTask, scriptTask, sendTask), one counting human task
 * flow-nodes (userTask, manualTask). Structural elements (events, gateways, sub-process containers)
 * match neither filter and are excluded from both numerator and denominator.
 *
 * <p>The view is bucket-agnostic — the group-by wraps this aggregation map inside each of its
 * buckets and calls {@link #retrieveResult} once per bucket, so the same interpreter serves NONE,
 * PROCESS_DEFINITION_KEY, and END_DATE plans.
 */
@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessViewAutomationRateInterpreterES implements ProcessViewInterpreterES {

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
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Aggregation.Builder.ContainerBuilder nested =
        new Aggregation.Builder()
            .nested(n -> n.path(FLOW_NODE_INSTANCES))
            .aggregations(AUTOMATED_TASKS_AGGREGATION, taskTypeFilter(AUTOMATED_TASK_TYPES))
            .aggregations(HUMAN_TASKS_AGGREGATION, taskTypeFilter(HUMAN_TASK_TYPES));
    return Map.of(AUTOMATION_NESTED_AGGREGATION, nested);
  }

  @Override
  public ViewResult retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggs,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final NestedAggregate nested = aggs.get(AUTOMATION_NESTED_AGGREGATION).nested();
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

  private static Aggregation taskTypeFilter(final List<String> taskTypes) {
    return Aggregation.of(
        a ->
            a.filter(
                f ->
                    f.terms(
                        t ->
                            t.field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TYPE)
                                .terms(
                                    tt ->
                                        tt.value(
                                            taskTypes.stream().map(FieldValue::of).toList())))));
  }
}
