/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process;

import static io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewAutomationRateInterpreterES.AUTOMATED_TASKS_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewAutomationRateInterpreterES.AUTOMATION_NESTED_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewAutomationRateInterpreterES.HUMAN_TASKS_AGGREGATION;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_INSTANCE_AUTOMATION_RATE;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_BUSINESS_RULE_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_MANUAL_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_SCRIPT_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_SEND_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_SERVICE_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_USER_TASK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProcessViewAutomationRateInterpreterESTest {

  private final ProcessViewAutomationRateInterpreterES interpreter =
      new ProcessViewAutomationRateInterpreterES();

  @Test
  void shouldReturnAutomationRateAsSupportedView() {
    // when
    final var supported = interpreter.getSupportedViews();

    // then
    assertThat(supported).containsExactly(PROCESS_VIEW_INSTANCE_AUTOMATION_RATE);
  }

  @Test
  void shouldEmitNestedAggregationOnFlowNodeInstances() {
    // when
    final Map<String, Aggregation.Builder.ContainerBuilder> aggs =
        interpreter.createAggregations(context());

    // then the root aggregation is a nested aggregation on the flowNodeInstances array path
    assertThat(aggs).hasSize(1).containsKey(AUTOMATION_NESTED_AGGREGATION);
    final Aggregation nested = aggs.get(AUTOMATION_NESTED_AGGREGATION).build();
    assertThat(nested.nested()).isNotNull();
    assertThat(nested.nested().path()).isEqualTo("flowNodeInstances");
    assertThat(nested.aggregations())
        .containsOnlyKeys(AUTOMATED_TASKS_AGGREGATION, HUMAN_TASKS_AGGREGATION);
  }

  @Test
  void shouldFilterAutomatedTasksByAllFourAutomatedTypes() {
    // given
    final Aggregation nested =
        interpreter.createAggregations(context()).get(AUTOMATION_NESTED_AGGREGATION).build();

    // when
    final Aggregation automatedFilter = nested.aggregations().get(AUTOMATED_TASKS_AGGREGATION);

    // then — the automated sub-aggregation is a terms filter over the flowNodeType field, matching
    // the four automated BPMN task types. Explicit list guards against silent divergence from the
    // Zeebe BpmnElementType names — see camunda-hub#26934 comment 1.
    assertThat(automatedFilter.filter().terms()).isNotNull();
    assertThat(automatedFilter.filter().terms().field())
        .isEqualTo("flowNodeInstances.flowNodeType");
    assertThat(automatedFilter.filter().terms().terms().value())
        .extracting(FieldValue::stringValue)
        .containsExactlyInAnyOrder(
            FLOW_NODE_TYPE_SERVICE_TASK,
            FLOW_NODE_TYPE_BUSINESS_RULE_TASK,
            FLOW_NODE_TYPE_SCRIPT_TASK,
            FLOW_NODE_TYPE_SEND_TASK);
  }

  @Test
  void shouldFilterHumanTasksByBothHumanTypes() {
    // given
    final Aggregation nested =
        interpreter.createAggregations(context()).get(AUTOMATION_NESTED_AGGREGATION).build();

    // when
    final Aggregation humanFilter = nested.aggregations().get(HUMAN_TASKS_AGGREGATION);

    // then — same shape as the automated filter, restricted to the two human BPMN task types.
    assertThat(humanFilter.filter().terms()).isNotNull();
    assertThat(humanFilter.filter().terms().field()).isEqualTo("flowNodeInstances.flowNodeType");
    assertThat(humanFilter.filter().terms().terms().value())
        .extracting(FieldValue::stringValue)
        .containsExactlyInAnyOrder(FLOW_NODE_TYPE_USER_TASK, FLOW_NODE_TYPE_MANUAL_TASK);
  }

  @Test
  void shouldReturn100WhenAllTasksAutomated() {
    // given four automated task instances and zero human task instances in the bucket
    final Map<String, Aggregate> aggs = aggs(4, 0);

    // when
    final ViewResult result = interpreter.retrieveResult(null, aggs, context());

    // then
    assertThat(result.getViewMeasures().get(0).getValue()).isEqualTo(100.0);
  }

  @Test
  void shouldReturn0WhenAllTasksHuman() {
    // given five human task instances and zero automated task instances in the bucket
    final Map<String, Aggregate> aggs = aggs(0, 5);

    // when
    final ViewResult result = interpreter.retrieveResult(null, aggs, context());

    // then
    assertThat(result.getViewMeasures().get(0).getValue()).isEqualTo(0.0);
  }

  @Test
  void shouldComputeRatioForMixedTasks() {
    // given three automated and one human task instance — 3 / (3 + 1) * 100 = 75.0
    final Map<String, Aggregate> aggs = aggs(3, 1);

    // when
    final ViewResult result = interpreter.retrieveResult(null, aggs, context());

    // then
    assertThat(result.getViewMeasures().get(0).getValue()).isEqualTo(75.0);
  }

  @Test
  void shouldReturnNullWhenNoTaskInstances() {
    // given a bucket with no matching task activity — e.g. a process built only from events,
    // gateways and call activities, or no completed instances in the selected range
    final Map<String, Aggregate> aggs = aggs(0, 0);

    // when
    final ViewResult result = interpreter.retrieveResult(null, aggs, context());

    // then null (empty state) rather than 0.0 so the frontend renders "—" instead of a value that
    // would visually equal a fully-manual process. See camunda-hub#26934 comment 2.
    assertThat(result.getViewMeasures().get(0).getValue()).isNull();
  }

  @Test
  void shouldReturnNullFromCreateEmptyResult() {
    // when
    final ViewResult result = interpreter.createEmptyResult(context());

    // then the empty-result path mirrors the zero-denominator path — no data yet, render "—"
    assertThat(result.getViewMeasures().get(0).getValue()).isNull();
  }

  private static Map<String, Aggregate> aggs(final long automatedCount, final long humanCount) {
    return Map.of(
        AUTOMATION_NESTED_AGGREGATION,
        Aggregate.of(
            a ->
                a.nested(
                    n ->
                        n.docCount(automatedCount + humanCount)
                            .aggregations(AUTOMATED_TASKS_AGGREGATION, filterAgg(automatedCount))
                            .aggregations(HUMAN_TASKS_AGGREGATION, filterAgg(humanCount)))));
  }

  private static Aggregate filterAgg(final long docCount) {
    return Aggregate.of(a -> a.filter(f -> f.docCount(docCount)));
  }

  @SuppressWarnings("unchecked")
  private static ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context() {
    return mock(ExecutionContext.class);
  }
}
