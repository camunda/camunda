/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.EvaluateConditionalCommandStep1;
import io.camunda.client.api.response.EvaluateConditionalResponse;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ConditionalEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ZeebeIntegration
public final class EvaluateConditionalTest {

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose CamundaClient client;
  ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  public void init() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldEvaluateConditionalWithProcessDefinitionKey(
      final boolean useRest, final TestInfo testInfo) {
    // given
    final String processId = "process-" + testInfo.getTestMethod().get().getName();
    final long processDefinitionKey = deployProcess(processId, "x > 50", useRest);

    // when
    final EvaluateConditionalResponse response =
        getCommand(client, useRest)
            .variables(Map.of("x", 100))
            .processDefinitionKey(processDefinitionKey)
            .send()
            .join();

    // then
    assertThat(response.getProcessInstances()).hasSize(1);
    final var processInstance = response.getProcessInstances().getFirst();
    assertThat(processInstance.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(processInstance.getProcessInstanceKey()).isPositive();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessDefinitionKey(processDefinitionKey)
                .withProcessInstanceKey(processInstance.getProcessInstanceKey())
                .limit(1))
        .hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldEvaluateConditionalWithoutProcessDefinitionKey(
      final boolean useRest, final TestInfo testInfo) {
    // given
    final String processId1 = "process1-" + testInfo.getTestMethod().get().getName();
    final String processId2 = "process2-" + testInfo.getTestMethod().get().getName();
    final long processDefinitionKey1 = deployProcess(processId1, "x > 50", useRest);
    final long processDefinitionKey2 = deployProcess(processId2, "x > 50", useRest);

    // when
    final EvaluateConditionalResponse response =
        getCommand(client, useRest).variables(Map.of("x", 100)).send().join();

    // then
    assertThat(response.getProcessInstances()).hasSize(2);

    final var processInstance1Key =
        response.getProcessInstances().stream()
            .filter(pi -> pi.getProcessDefinitionKey() == processDefinitionKey1)
            .findFirst()
            .orElseThrow()
            .getProcessInstanceKey();
    final var processInstance2Key =
        response.getProcessInstances().stream()
            .filter(pi -> pi.getProcessDefinitionKey() == processDefinitionKey2)
            .findFirst()
            .orElseThrow()
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
        .containsExactlyInAnyOrder(
            tuple(processDefinitionKey1, processInstance1Key),
            tuple(processDefinitionKey2, processInstance2Key));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldReturnEmptyWhenNoConditionsMatch(
      final boolean useRest, final TestInfo testInfo) {
    // given
    final String processId = "process-" + testInfo.getTestMethod().get().getName();
    deployProcess(processId, "x > 50", useRest);

    // when
    final EvaluateConditionalResponse response =
        getCommand(client, useRest).variables(Map.of("x", 10)).send().join();

    // then
    assertThat(response.getProcessInstances()).isEmpty();

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

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldEvaluateProcessWithMultipleMatchingStartEvents(
      final boolean useRest, final TestInfo testInfo) {
    // given
    final String processId = "process-" + testInfo.getTestMethod().get().getName();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start1")
            .condition("x > 500")
            .endEvent("end1")
            .moveToProcess(processId)
            .startEvent("start2")
            .condition("y < 200")
            .endEvent("end2")
            .done();
    final long processDefinitionKey = resourcesHelper.deployProcess(process, useRest);

    // when
    final EvaluateConditionalResponse response =
        getCommand(client, useRest)
            .variables(Map.of("x", 1000, "y", 100))
            .processDefinitionKey(processDefinitionKey)
            .send()
            .join();

    // then
    assertThat(response.getProcessInstances()).hasSize(2);
    assertThat(response.getProcessInstances())
        .allSatisfy(
            pi -> {
              assertThat(pi.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
              assertThat(pi.getProcessInstanceKey()).isPositive();
            });

    final var processInstance1Key = response.getProcessInstances().get(0).getProcessInstanceKey();
    final var processInstance2Key = response.getProcessInstances().get(1).getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementType(BpmnElementType.PROCESS)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            ProcessInstanceRecordValue::getProcessDefinitionKey,
            ProcessInstanceRecordValue::getProcessInstanceKey)
        .containsExactlyInAnyOrder(
            tuple(processDefinitionKey, processInstance1Key),
            tuple(processDefinitionKey, processInstance2Key));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectEvaluateConditionalWithNonExistingProcessDefinitionKey(
      final boolean useRest) {
    // given
    final long nonExistingKey = 123L;

    // when
    final var command =
        getCommand(client, useRest)
            .variables(Map.of("x", 100))
            .processDefinitionKey(nonExistingKey)
            .send();

    // then
    assertThatThrownBy(command::join)
        .hasMessageContaining(
            "Expected to evaluate conditional for process definition key '%s', but no such process was found"
                .formatted(nonExistingKey));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectEvaluateConditionalWithTenantIdWhenNoMultiTenancy(final boolean useRest) {
    // given
    final long nonExistingKey = 123L;
    final var tenantId = "tenant-1";

    // when
    final var command =
        getCommand(client, useRest)
            .variables(Map.of("x", 100))
            .processDefinitionKey(nonExistingKey)
            .tenantId(tenantId)
            .send();

    // then
    final var expectedRequestName = useRest ? "Evaluate Conditional" : "EvaluateConditional";
    assertThatThrownBy(command::join)
        .hasMessageContaining(
            "Expected to handle request %s with tenant identifier '%s', but multi-tenancy is disabled"
                .formatted(expectedRequestName, tenantId));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldThrowErrorWhenTryToEvaluateConditionalWithNullVariable(final boolean useRest) {
    // when/then
    assertThatThrownBy(() -> getCommand(client, useRest).variable(null, null).send().join())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldEvaluateConditionalForLatestVersionOnly(
      final boolean useRest, final TestInfo testInfo) {
    // given
    final String processId = "process-" + testInfo.getTestMethod().get().getName();
    final long processDefinitionKeyV1 = deployProcess(processId, "x > 50", useRest);
    final long processDefinitionKeyV2 = deployProcess(processId, "x > 50", useRest);

    // when
    final EvaluateConditionalResponse response =
        getCommand(client, useRest).variables(Map.of("x", 100)).send().join();

    // then
    assertThat(response.getProcessInstances()).hasSize(1);
    final var processInstance = response.getProcessInstances().getFirst();
    assertThat(processInstance.getProcessDefinitionKey()).isEqualTo(processDefinitionKeyV2);
    assertThat(processInstance.getProcessInstanceKey()).isPositive();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementType(BpmnElementType.PROCESS)
                .withProcessDefinitionKey(processDefinitionKeyV2)
                .limit(1))
        .extracting(Record::getValue)
        .extracting(
            ProcessInstanceRecordValue::getProcessDefinitionKey,
            ProcessInstanceRecordValue::getVersion,
            ProcessInstanceRecordValue::getProcessInstanceKey)
        .containsExactly(tuple(processDefinitionKeyV2, 2, processInstance.getProcessInstanceKey()));

    assertThat(
            RecordingExporter.records()
                .betweenProcessInstance(processInstance.getProcessInstanceKey())
                .processInstanceRecords()
                .withProcessDefinitionKey(processDefinitionKeyV1)
                .withVersion(1)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isFalse();
  }

  private EvaluateConditionalCommandStep1 getCommand(
      final CamundaClient client, final boolean useRest) {
    final EvaluateConditionalCommandStep1 command = client.newEvaluateConditionalCommand();
    return useRest ? command.useRest() : command.useGrpc();
  }

  private long deployProcess(
      final String processId, final String condition, final boolean useRest) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .condition(condition)
            .endEvent()
            .done();
    return resourcesHelper.deployProcess(process, useRest);
  }
}
