/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.conditional.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ConditionalEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ConditionalEvaluationTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  private String processId;

  @Before
  public void init() {
    processId = helper.getBpmnProcessId();
  }

  @Test
  public void shouldEvaluateConditionalAndCreateProcessInstance() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent("start")
                .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                .endEvent()
                .done())
        .deploy();

    // when
    engine.conditionalEvaluation().withVariables(Map.of("x", 1000, "y", 100)).evaluate();

    // then
    assertThat(
            RecordingExporter.conditionalEvaluationRecords(ConditionalEvaluationIntent.EVALUATED)
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withBpmnProcessId(processId)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldEvaluateMultipleMatchingConditionals() {
    // given
    final String process1 = "process-" + UUID.randomUUID();
    final String process2 = "process-" + UUID.randomUUID();

    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(process1)
                .startEvent()
                .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                .endEvent()
                .done())
        .deploy();

    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(process2)
                .startEvent()
                .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                .endEvent()
                .done())
        .deploy();

    // when
    engine.conditionalEvaluation().withVariables(Map.of("x", 1000, "y", 100)).evaluate();

    // then
    assertThat(
            RecordingExporter.conditionalEvaluationRecords(ConditionalEvaluationIntent.EVALUATED)
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withBpmnProcessId(process1)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withBpmnProcessId(process2)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldWriteEvaluatedEventWhenNoConditionalsMatch() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .condition(c -> c.condition("=false").zeebeVariableNames("x, y"))
                .endEvent()
                .done())
        .deploy();

    // when
    engine.conditionalEvaluation().withVariables(Map.of("x", 1000, "y", 100)).evaluate();

    // then
    assertThat(
            RecordingExporter.conditionalEvaluationRecords(ConditionalEvaluationIntent.EVALUATED)
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withBpmnProcessId(processId)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldNotMatchConditionalWithNonMatchingVariableName() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                .endEvent()
                .done())
        .deploy();

    // when
    engine.conditionalEvaluation().withVariables(Map.of("a", 1000, "b", "test")).evaluate();

    // then
    assertThat(
            RecordingExporter.conditionalEvaluationRecords(ConditionalEvaluationIntent.EVALUATED)
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withBpmnProcessId(processId)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldMatchConditionalWhenOneOfMultipleVariablesMatches() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y, z"))
                .endEvent()
                .done())
        .deploy();

    // when
    engine
        .conditionalEvaluation()
        .withVariables(Map.of("x", 1000, "y", 100, "a", "123"))
        .evaluate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withBpmnProcessId(processId)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldEvaluateConditionWithVariableReference() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .condition(c -> c.condition("=orderAmount > 500").zeebeVariableNames("orderAmount"))
                .endEvent()
                .done())
        .deploy();

    // when
    engine.conditionalEvaluation().withVariable("orderAmount", 1000).evaluate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withBpmnProcessId(processId)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldNotMatchWhenConditionEvaluatesToFalse() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .condition(c -> c.condition("=orderAmount > 500").zeebeVariableNames("orderAmount"))
                .endEvent()
                .done())
        .deploy();

    // when
    engine.conditionalEvaluation().withVariable("orderAmount", 100).evaluate();

    // then
    assertThat(
            RecordingExporter.conditionalEvaluationRecords(ConditionalEvaluationIntent.EVALUATED)
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withBpmnProcessId(processId)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldEvaluateComplexConditionWithMultipleVariables() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .condition(
                    c ->
                        c.condition("=status = \"VIP\" and orderAmount > 500")
                            .zeebeVariableNames("status, orderAmount"))
                .endEvent()
                .done())
        .deploy();

    // when
    engine
        .conditionalEvaluation()
        .withVariables(Map.of("status", "VIP", "orderAmount", 1000))
        .evaluate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withBpmnProcessId(processId)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldEvaluateSpecificProcessByKey() {
    // given
    final var deployment1 =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process-" + UUID.randomUUID())
                    .startEvent()
                    .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                    .endEvent()
                    .done())
            .deploy();

    final var deployment2 =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process-" + UUID.randomUUID())
                    .startEvent()
                    .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                    .endEvent()
                    .done())
            .deploy();

    final long process1Key =
        deployment1.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();
    final long process2Key =
        deployment2.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    // when
    engine
        .conditionalEvaluation()
        .withProcessDefinitionKey(process1Key)
        .withVariables(Map.of("x", 100, "y", 1))
        .evaluate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withProcessDefinitionKey(process1Key)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withProcessDefinitionKey(process2Key)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldEvaluateAllProcessesWhenNoKeyProvided() {
    // given
    final var deployment1 =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process-" + UUID.randomUUID())
                    .startEvent()
                    .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                    .endEvent()
                    .done())
            .deploy();

    final var deployment2 =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process-" + UUID.randomUUID())
                    .startEvent()
                    .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                    .endEvent()
                    .done())
            .deploy();

    final long process1Key =
        deployment1.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();
    final long process2Key =
        deployment2.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    // when
    engine.conditionalEvaluation().withVariables(Map.of("x", 100, "y", 1)).evaluate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withProcessDefinitionKey(process1Key)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withProcessDefinitionKey(process2Key)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldRejectWhenProcessDefinitionKeyNotFound() {
    // given
    final long nonExistentKey = 999999L;

    // when
    final var rejection =
        engine
            .conditionalEvaluation()
            .withProcessDefinitionKey(nonExistentKey)
            .withVariables(Map.of("x", 100, "y", 1))
            .expectRejection()
            .evaluate();

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldEvaluateConditionalForSpecificTenant() {
    // given
    final String tenant1 = "tenant1";
    final String tenant2 = "tenant2";
    final String process1Id = "process-" + UUID.randomUUID();
    final String process2Id = "process-" + UUID.randomUUID();

    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(process1Id)
                .startEvent()
                .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                .endEvent()
                .done())
        .withTenantId(tenant1)
        .deploy();

    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(process2Id)
                .startEvent()
                .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                .endEvent()
                .done())
        .withTenantId(tenant2)
        .deploy();

    // when
    engine
        .conditionalEvaluation()
        .withTenantId(tenant1)
        .withVariables(Map.of("x", 100, "y", 1))
        .evaluate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withBpmnProcessId(process1Id)
                .withTenantId(tenant1)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withTenantId(tenant2)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldNotMatchWhenOnlyPartialVariablesGiven() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent("start")
                .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                .endEvent()
                .done())
        .deploy();

    // when
    engine.conditionalEvaluation().withVariables(Map.of("x", 1000)).evaluate();

    // then
    assertThat(
            RecordingExporter.conditionalEvaluationRecords(ConditionalEvaluationIntent.EVALUATED)
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withBpmnProcessId(processId)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldWriteEvaluatedRecordWithCorrectValues() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                .endEvent()
                .done())
        .deploy();

    final Map<String, Object> variables = Map.of("x", 100, "y", 10, "z", "test");

    // when
    final var record = engine.conditionalEvaluation().withVariables(variables).evaluate();

    // then
    Assertions.assertThat(record).hasIntent(ConditionalEvaluationIntent.EVALUATED);
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withBpmnProcessId(processId)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
    assertThat(record.getValue().getVariables()).containsAllEntriesOf(variables);
  }
}
