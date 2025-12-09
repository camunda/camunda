/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.conditional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ConditionalEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
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
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start")
                    .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                    .endEvent()
                    .done())
            .deploy();

    final var processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    // when
    final var evaluatedRecord =
        engine.conditionalEvaluation().withVariables(Map.of("x", 1000, "y", 100)).evaluate();

    // then
    assertThat(evaluatedRecord.getIntent()).isEqualTo(ConditionalEvaluationIntent.EVALUATED);

    Assertions.assertThat(evaluatedRecord.getValue())
        .hasProcessDefinitionKey(-1L)
        .hasVariables(Map.of("x", 1000, "y", 100))
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(evaluatedRecord.getValue().getStartedProcessInstances())
        .hasSize(1)
        .first()
        .satisfies(
            instance -> {
              assertThat(instance.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
              assertThat(instance.getProcessInstanceKey()).isPositive();
            });

    final var processInstanceKey =
        evaluatedRecord.getValue().getStartedProcessInstances().getFirst().getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(processId)
                .withElementType(BpmnElementType.PROCESS)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldEvaluateProcessWithMultipleStartEventsAndMatchOnlyOne() {
    // given
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start1")
                    .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                    .endEvent("end1")
                    .moveToProcess(processId)
                    .startEvent("start2")
                    .condition(c -> c.condition("=x < 500").zeebeVariableNames("x"))
                    .endEvent("end2")
                    .done())
            .deploy();

    final var processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    // when
    final var evaluatedRecord =
        engine.conditionalEvaluation().withVariables(Map.of("x", 1000, "y", 100)).evaluate();

    // then
    assertThat(evaluatedRecord.getIntent()).isEqualTo(ConditionalEvaluationIntent.EVALUATED);

    Assertions.assertThat(evaluatedRecord.getValue())
        .hasProcessDefinitionKey(-1L)
        .hasVariables(Map.of("x", 1000, "y", 100))
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(evaluatedRecord.getValue().getStartedProcessInstances())
        .hasSize(1)
        .first()
        .satisfies(
            instance -> {
              assertThat(instance.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
              assertThat(instance.getProcessInstanceKey()).isPositive();
            });

    final var processInstanceKey =
        evaluatedRecord.getValue().getStartedProcessInstances().getFirst().getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("start1", ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple("start1", ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .doesNotContainSequence(
            tuple("start2", ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple("start2", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldEvaluateProcessWithMultipleStartEventsAndMatchBoth() {
    // given
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start1")
                    .condition(c -> c.condition("=x > 500").zeebeVariableNames("x"))
                    .endEvent("end1")
                    .moveToProcess(processId)
                    .startEvent("start2")
                    .condition(c -> c.condition("=y < 200").zeebeVariableNames("y"))
                    .endEvent("end2")
                    .done())
            .deploy();

    final var processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    // when
    final var evaluatedRecord =
        engine.conditionalEvaluation().withVariables(Map.of("x", 1000, "y", 100)).evaluate();

    // then
    assertThat(evaluatedRecord.getIntent()).isEqualTo(ConditionalEvaluationIntent.EVALUATED);

    Assertions.assertThat(evaluatedRecord.getValue())
        .hasProcessDefinitionKey(-1L)
        .hasVariables(Map.of("x", 1000, "y", 100))
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(evaluatedRecord.getValue().getStartedProcessInstances())
        .hasSize(2)
        .allSatisfy(
            instance -> {
              assertThat(instance.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
              assertThat(instance.getProcessInstanceKey()).isPositive();
            });

    final var startedInstances = evaluatedRecord.getValue().getStartedProcessInstances();
    final var processInstance1Key = startedInstances.get(0).getProcessInstanceKey();
    final var processInstance2Key = startedInstances.get(1).getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementType(BpmnElementType.PROCESS)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            ProcessInstanceRecordValue::getProcessDefinitionKey,
            ProcessInstanceRecordValue::getProcessInstanceKey)
        .hasSize(2)
        .containsExactlyInAnyOrder(
            tuple(processDefinitionKey, processInstance1Key),
            tuple(processDefinitionKey, processInstance2Key));
  }

  @Test
  public void shouldEvaluateMultipleMatchingConditionals() {
    // given
    final String process1 = "process-" + UUID.randomUUID();
    final String process2 = "process-" + UUID.randomUUID();

    final var deployment1 =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(process1)
                    .startEvent()
                    .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                    .endEvent()
                    .done())
            .deploy();

    final var processDefinitionKey1 =
        deployment1.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    final var deployment2 =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(process2)
                    .startEvent()
                    .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                    .endEvent()
                    .done())
            .deploy();

    final var processDefinitionKey2 =
        deployment2.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    // when
    final var evaluatedRecord =
        engine.conditionalEvaluation().withVariables(Map.of("x", 1000, "y", 100)).evaluate();

    // then
    assertThat(evaluatedRecord.getIntent()).isEqualTo(ConditionalEvaluationIntent.EVALUATED);

    Assertions.assertThat(evaluatedRecord.getValue())
        .hasProcessDefinitionKey(-1L)
        .hasVariables(Map.of("x", 1000, "y", 100))
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(evaluatedRecord.getValue().getStartedProcessInstances())
        .hasSize(2)
        .satisfiesExactlyInAnyOrder(
            instance -> {
              assertThat(instance.getProcessDefinitionKey()).isEqualTo(processDefinitionKey1);
              assertThat(instance.getProcessInstanceKey()).isPositive();
            },
            instance -> {
              assertThat(instance.getProcessDefinitionKey()).isEqualTo(processDefinitionKey2);
              assertThat(instance.getProcessInstanceKey()).isPositive();
            });

    final var startedInstances = evaluatedRecord.getValue().getStartedProcessInstances();
    final var processInstance1Key =
        startedInstances.stream()
            .filter(i -> i.getProcessDefinitionKey() == processDefinitionKey1)
            .findFirst()
            .get()
            .getProcessInstanceKey();
    final var processInstance2Key =
        startedInstances.stream()
            .filter(i -> i.getProcessDefinitionKey() == processDefinitionKey2)
            .findFirst()
            .get()
            .getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementType(BpmnElementType.PROCESS)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            ProcessInstanceRecordValue::getProcessDefinitionKey,
            ProcessInstanceRecordValue::getProcessInstanceKey)
        .hasSize(2)
        .containsExactlyInAnyOrder(
            tuple(processDefinitionKey1, processInstance1Key),
            tuple(processDefinitionKey2, processInstance2Key));
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
    final var evaluatedRecord =
        engine.conditionalEvaluation().withVariables(Map.of("x", 1000, "y", 100)).evaluate();

    // then
    assertThat(evaluatedRecord.getIntent()).isEqualTo(ConditionalEvaluationIntent.EVALUATED);

    Assertions.assertThat(evaluatedRecord.getValue())
        .hasProcessDefinitionKey(-1L)
        .hasVariables(Map.of("x", 1000, "y", 100))
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .hasNoStartedProcessInstances();

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == ConditionalEvaluationIntent.EVALUATE,
                    r -> r.getIntent() == ConditionalEvaluationIntent.EVALUATED)
                .processInstanceRecords()
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
    final var evaluatedRecord =
        engine.conditionalEvaluation().withVariables(Map.of("a", 1000, "b", "test")).evaluate();

    // then
    assertThat(evaluatedRecord.getIntent()).isEqualTo(ConditionalEvaluationIntent.EVALUATED);

    Assertions.assertThat(evaluatedRecord.getValue())
        .hasProcessDefinitionKey(-1L)
        .hasVariables(Map.of("a", 1000, "b", "test"))
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .hasNoStartedProcessInstances();

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == ConditionalEvaluationIntent.EVALUATE,
                    r -> r.getIntent() == ConditionalEvaluationIntent.EVALUATED)
                .processInstanceRecords()
                .withBpmnProcessId(processId)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldMatchConditionalWhenOneOfMultipleVariablesMatches() {
    // given
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y, z"))
                    .endEvent()
                    .done())
            .deploy();

    final var processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    // when
    final var evaluatedRecord =
        engine
            .conditionalEvaluation()
            .withVariables(Map.of("x", 1000, "y", 100, "a", "123"))
            .evaluate();

    // then
    assertThat(evaluatedRecord.getIntent()).isEqualTo(ConditionalEvaluationIntent.EVALUATED);

    Assertions.assertThat(evaluatedRecord.getValue())
        .hasProcessDefinitionKey(-1L)
        .hasVariables(Map.of("x", 1000, "y", 100, "a", "123"))
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(evaluatedRecord.getValue().getStartedProcessInstances())
        .hasSize(1)
        .first()
        .satisfies(
            instance -> {
              assertThat(instance.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
              assertThat(instance.getProcessInstanceKey()).isPositive();
            });

    final var processInstanceKey =
        evaluatedRecord.getValue().getStartedProcessInstances().getFirst().getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(processId)
                .withElementType(BpmnElementType.PROCESS)
                .withProcessInstanceKey(processInstanceKey)
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
    final var evaluatedRecord =
        engine.conditionalEvaluation().withVariable("orderAmount", 100).evaluate();

    // then
    assertThat(evaluatedRecord.getIntent()).isEqualTo(ConditionalEvaluationIntent.EVALUATED);

    Assertions.assertThat(evaluatedRecord.getValue())
        .hasProcessDefinitionKey(-1L)
        .hasVariables(Map.of("orderAmount", 100))
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .hasNoStartedProcessInstances();

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == ConditionalEvaluationIntent.EVALUATE,
                    r -> r.getIntent() == ConditionalEvaluationIntent.EVALUATED)
                .processInstanceRecords()
                .withBpmnProcessId(processId)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldEvaluateComplexConditionWithMultipleVariables() {
    // given
    final var deployment =
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

    final var processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    // when
    final var evaluatedRecord =
        engine
            .conditionalEvaluation()
            .withVariables(Map.of("status", "VIP", "orderAmount", 1000))
            .evaluate();

    // then
    assertThat(evaluatedRecord.getIntent()).isEqualTo(ConditionalEvaluationIntent.EVALUATED);

    Assertions.assertThat(evaluatedRecord.getValue())
        .hasProcessDefinitionKey(-1L)
        .hasVariables(Map.of("status", "VIP", "orderAmount", 1000))
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(evaluatedRecord.getValue().getStartedProcessInstances())
        .hasSize(1)
        .first()
        .satisfies(
            instance -> {
              assertThat(instance.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
              assertThat(instance.getProcessInstanceKey()).isPositive();
            });

    final var processInstanceKey =
        evaluatedRecord.getValue().getStartedProcessInstances().getFirst().getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(processId)
                .withElementType(BpmnElementType.PROCESS)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldEvaluateConditionWithNestedVariableAccess() {
    // given
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .condition(c -> c.condition("=order.total > 50").zeebeVariableNames("order"))
                    .endEvent()
                    .done())
            .deploy();

    final var processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    // when
    final var evaluatedRecord =
        engine
            .conditionalEvaluation()
            .withVariables(Map.of("order", Map.of("total", 51)))
            .evaluate();

    // then
    assertThat(evaluatedRecord.getIntent()).isEqualTo(ConditionalEvaluationIntent.EVALUATED);

    Assertions.assertThat(evaluatedRecord.getValue())
        .hasProcessDefinitionKey(-1L)
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(evaluatedRecord.getValue().getVariables()).containsOnlyKeys("order");

    assertThat(evaluatedRecord.getValue().getStartedProcessInstances())
        .hasSize(1)
        .first()
        .satisfies(
            instance -> {
              assertThat(instance.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
              assertThat(instance.getProcessInstanceKey()).isPositive();
            });

    final var processInstanceKey =
        evaluatedRecord.getValue().getStartedProcessInstances().getFirst().getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(processId)
                .withElementType(BpmnElementType.PROCESS)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldEvaluateSpecificProcessByKey() {
    // given
    final var process1Id = "process-" + UUID.randomUUID();
    final var process2Id = "process-" + UUID.randomUUID();

    final var deployment1 =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(process1Id)
                    .startEvent()
                    .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                    .endEvent()
                    .done())
            .deploy();

    final var deployment2 =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(process2Id)
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
    final var evaluatedRecord =
        engine
            .conditionalEvaluation()
            .withProcessDefinitionKey(process1Key)
            .withVariables(Map.of("x", 100, "y", 1))
            .evaluate();

    // then
    assertThat(evaluatedRecord.getIntent()).isEqualTo(ConditionalEvaluationIntent.EVALUATED);

    Assertions.assertThat(evaluatedRecord.getValue())
        .hasProcessDefinitionKey(process1Key)
        .hasVariables(Map.of("x", 100, "y", 1))
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(evaluatedRecord.getValue().getStartedProcessInstances())
        .hasSize(1)
        .first()
        .satisfies(
            instance -> {
              assertThat(instance.getProcessDefinitionKey()).isEqualTo(process1Key);
              assertThat(instance.getProcessInstanceKey()).isPositive();
            });

    final var processInstanceKey =
        evaluatedRecord.getValue().getStartedProcessInstances().getFirst().getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessDefinitionKey(process1Key)
                .withElementType(BpmnElementType.PROCESS)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == ConditionalEvaluationIntent.EVALUATE,
                    r ->
                        r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED
                            && ((ProcessInstanceRecordValue) r.getValue())
                                .getBpmnProcessId()
                                .equals(process1Id))
                .processInstanceRecords()
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
    final var evaluatedRecord =
        engine.conditionalEvaluation().withVariables(Map.of("x", 100, "y", 1)).evaluate();

    // then
    assertThat(evaluatedRecord.getIntent()).isEqualTo(ConditionalEvaluationIntent.EVALUATED);
    Assertions.assertThat(evaluatedRecord.getValue())
        .hasProcessDefinitionKey(-1L)
        .hasVariables(Map.of("x", 100, "y", 1))
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(evaluatedRecord.getValue().getStartedProcessInstances())
        .hasSize(2)
        .satisfiesExactlyInAnyOrder(
            instance -> {
              assertThat(instance.getProcessDefinitionKey()).isEqualTo(process1Key);
              assertThat(instance.getProcessInstanceKey()).isPositive();
            },
            instance -> {
              assertThat(instance.getProcessDefinitionKey()).isEqualTo(process2Key);
              assertThat(instance.getProcessInstanceKey()).isPositive();
            });

    final var startedInstances = evaluatedRecord.getValue().getStartedProcessInstances();
    final var processInstance1Key =
        startedInstances.stream()
            .filter(i -> i.getProcessDefinitionKey() == process1Key)
            .findFirst()
            .get()
            .getProcessInstanceKey();
    final var processInstance2Key =
        startedInstances.stream()
            .filter(i -> i.getProcessDefinitionKey() == process2Key)
            .findFirst()
            .get()
            .getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementType(BpmnElementType.PROCESS)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            ProcessInstanceRecordValue::getProcessDefinitionKey,
            ProcessInstanceRecordValue::getProcessInstanceKey)
        .hasSize(2)
        .containsExactlyInAnyOrder(
            tuple(process1Key, processInstance1Key), tuple(process2Key, processInstance2Key));
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

    final var deployment1 =
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

    final var process1Key =
        deployment1.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

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
    final var evaluatedRecord =
        engine
            .conditionalEvaluation()
            .withTenantId(tenant1)
            .withVariables(Map.of("x", 100, "y", 1))
            .evaluate();

    // then
    assertThat(evaluatedRecord.getIntent()).isEqualTo(ConditionalEvaluationIntent.EVALUATED);

    Assertions.assertThat(evaluatedRecord.getValue())
        .hasProcessDefinitionKey(-1L)
        .hasVariables(Map.of("x", 100, "y", 1))
        .hasTenantId(tenant1);

    assertThat(evaluatedRecord.getValue().getStartedProcessInstances())
        .hasSize(1)
        .first()
        .satisfies(
            instance -> {
              assertThat(instance.getProcessDefinitionKey()).isEqualTo(process1Key);
              assertThat(instance.getProcessInstanceKey()).isPositive();
            });

    final var processInstanceKey =
        evaluatedRecord.getValue().getStartedProcessInstances().getFirst().getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(process1Id)
                .withTenantId(tenant1)
                .withElementType(BpmnElementType.PROCESS)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == ConditionalEvaluationIntent.EVALUATE,
                    r ->
                        r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED
                            && ((ProcessInstanceRecordValue) r.getValue())
                                .getBpmnProcessId()
                                .equals(process1Id))
                .processInstanceRecords()
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
    final var evaluatedRecord =
        engine.conditionalEvaluation().withVariables(Map.of("x", 1000)).evaluate();

    // then
    assertThat(evaluatedRecord.getIntent()).isEqualTo(ConditionalEvaluationIntent.EVALUATED);

    Assertions.assertThat(evaluatedRecord.getValue())
        .hasProcessDefinitionKey(-1L)
        .hasVariables(Map.of("x", 1000))
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .hasNoStartedProcessInstances();

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == ConditionalEvaluationIntent.EVALUATE,
                    r -> r.getIntent() == ConditionalEvaluationIntent.EVALUATED)
                .processInstanceRecords()
                .withBpmnProcessId(processId)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldWriteEvaluatedRecordWithCorrectValues() {
    // given
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                    .endEvent()
                    .done())
            .deploy();

    final var processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    final Map<String, Object> variables = Map.of("x", 100, "y", 10, "z", "test");

    // when
    final var record = engine.conditionalEvaluation().withVariables(variables).evaluate();

    // then
    Assertions.assertThat(record).hasIntent(ConditionalEvaluationIntent.EVALUATED);

    Assertions.assertThat(record.getValue())
        .hasProcessDefinitionKey(-1L)
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(record.getValue().getVariables()).containsAllEntriesOf(variables);

    assertThat(record.getValue().getStartedProcessInstances())
        .hasSize(1)
        .first()
        .satisfies(
            instance -> {
              assertThat(instance.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
              assertThat(instance.getProcessInstanceKey()).isPositive();
            });

    final var processInstanceKey =
        record.getValue().getStartedProcessInstances().getFirst().getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(processId)
                .withElementType(BpmnElementType.PROCESS)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }
}
