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
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ZeebeIntegration
public final class CreateProcessInstanceTest {

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose CamundaClient client;
  ZeebeResourcesHelper resourcesHelper;
  private String processId;
  private String processId2;
  private long firstProcessDefinitionKey;
  private long secondProcessDefinitionKey;

  @BeforeEach
  public void init() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCreateBpmnProcessById(final boolean useRest, final TestInfo testInfo) {
    // given
    deployProcesses(testInfo, useRest);

    // when
    final ProcessInstanceEvent processInstance =
        getCommand(client, useRest).bpmnProcessId(processId).latestVersion().send().join();

    // then
    assertThat(processInstance.getBpmnProcessId()).isEqualTo(processId);
    assertThat(processInstance.getVersion()).isEqualTo(2);
    assertThat(processInstance.getProcessDefinitionKey()).isEqualTo(secondProcessDefinitionKey);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCreateBpmnProcessByIdAndVersion(
      final boolean useRest, final TestInfo testInfo) {
    // given
    deployProcesses(testInfo, useRest);

    // when
    final ProcessInstanceEvent processInstance =
        getCommand(client, useRest).bpmnProcessId(processId).version(1).send().join();

    // then instance is created of first process version
    assertThat(processInstance.getBpmnProcessId()).isEqualTo(processId);
    assertThat(processInstance.getVersion()).isEqualTo(1);
    assertThat(processInstance.getProcessDefinitionKey()).isEqualTo(firstProcessDefinitionKey);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCreateBpmnProcessByKey(final boolean useRest, final TestInfo testInfo) {
    // given
    deployProcesses(testInfo, useRest);

    // when
    final ProcessInstanceEvent processInstance =
        getCommand(client, useRest).processDefinitionKey(firstProcessDefinitionKey).send().join();

    // then
    assertThat(processInstance.getBpmnProcessId()).isEqualTo(processId);
    assertThat(processInstance.getVersion()).isEqualTo(1);
    assertThat(processInstance.getProcessDefinitionKey()).isEqualTo(firstProcessDefinitionKey);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCreateWithVariables(final boolean useRest, final TestInfo testInfo) {
    // given
    deployProcesses(testInfo, useRest);
    final Map<String, Object> variables = Map.of("foo", 123);

    // when
    final ProcessInstanceEvent event =
        getCommand(client, useRest)
            .bpmnProcessId(processId)
            .latestVersion()
            .variables(variables)
            .send()
            .join();

    // then
    final var createdEvent =
        RecordingExporter.processInstanceCreationRecords()
            .withIntent(ProcessInstanceCreationIntent.CREATED)
            .withInstanceKey(event.getProcessInstanceKey())
            .getFirst();

    assertThat(createdEvent.getValue().getVariables()).containsExactlyEntriesOf(variables);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCreateWithTags(final boolean useRest, final TestInfo testInfo) {
    // given
    deployProcesses(testInfo, useRest);
    final Set<String> tags = Set.of("foo", "bar");

    // when
    final ProcessInstanceEvent event =
        getCommand(client, useRest)
            .bpmnProcessId(processId)
            .latestVersion()
            .tags(tags)
            .send()
            .join();

    // then
    final var createdEvent =
        RecordingExporter.processInstanceCreationRecords()
            .withIntent(ProcessInstanceCreationIntent.CREATED)
            .withInstanceKey(event.getProcessInstanceKey())
            .getFirst();

    assertThat(createdEvent.getValue().getTags()).isEqualTo(tags);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCreateWithoutVariables(final boolean useRest, final TestInfo testInfo) {
    // given
    deployProcesses(testInfo, useRest);

    // when
    final ProcessInstanceEvent event =
        getCommand(client, useRest).bpmnProcessId(processId).latestVersion().send().join();

    // then
    final var createdEvent =
        RecordingExporter.processInstanceCreationRecords()
            .withIntent(ProcessInstanceCreationIntent.CREATED)
            .withInstanceKey(event.getProcessInstanceKey())
            .getFirst();

    assertThat(createdEvent.getValue().getVariables()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCreateWithNullVariables(final boolean useRest, final TestInfo testInfo) {
    // given
    deployProcesses(testInfo, useRest);

    // when
    final ProcessInstanceEvent event =
        getCommand(client, useRest)
            .bpmnProcessId(processId)
            .latestVersion()
            .variables("null")
            .send()
            .join();

    // then
    final var createdEvent =
        RecordingExporter.processInstanceCreationRecords()
            .withIntent(ProcessInstanceCreationIntent.CREATED)
            .withInstanceKey(event.getProcessInstanceKey())
            .getFirst();

    assertThat(createdEvent.getValue().getVariables()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCreateWithSingleVariable(final boolean useRest, final TestInfo testInfo) {
    // given
    deployProcesses(testInfo, useRest);
    final String key = "key";
    final String value = "value";

    // when
    final ProcessInstanceEvent event =
        getCommand(client, useRest)
            .bpmnProcessId(processId)
            .latestVersion()
            .variable(key, value)
            .send()
            .join();

    // then
    final var createdEvent =
        RecordingExporter.processInstanceCreationRecords()
            .withIntent(ProcessInstanceCreationIntent.CREATED)
            .withInstanceKey(event.getProcessInstanceKey())
            .getFirst();

    assertThat(createdEvent.getValue().getVariables()).containsExactlyEntriesOf(Map.of(key, value));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldThrowErrorWhenTryToCreateInstanceWithNullVariable(
      final boolean useRest, final TestInfo testInfo) {
    // given
    deployProcesses(testInfo, useRest);

    // when
    assertThatThrownBy(
            () ->
                getCommand(client, useRest)
                    .bpmnProcessId(processId)
                    .latestVersion()
                    .variable(null, null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectCompleteJobIfVariablesAreInvalid(
      final boolean useRest, final TestInfo testInfo) {
    // given
    deployProcesses(testInfo, useRest);

    // when
    if (useRest) {
      assertThatThrownBy(
              () ->
                  getCommand(client, useRest)
                      .bpmnProcessId(processId)
                      .latestVersion()
                      .variables("[]")
                      .send()
                      .join())
          .hasMessageContaining("Failed to deserialize json '[]' to 'Map<String, Object>'");
    } else {
      assertThatThrownBy(
              () ->
                  getCommand(client, useRest)
                      .bpmnProcessId(processId)
                      .latestVersion()
                      .variables("[]")
                      .send()
                      .join())
          .hasMessageContaining(
              "Property 'variables' is invalid: Expected document to be a root level object, but was 'ARRAY'");
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectCreateBpmnProcessByNonExistingId(final boolean useRest) {
    // when
    final var command =
        getCommand(client, useRest).bpmnProcessId("non-existing").latestVersion().send();

    assertThatThrownBy(command::join)
        .hasMessageContaining(
            "Expected to find process definition with process ID 'non-existing', but none found");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectCreateBpmnProcessByNonExistingKey(final boolean useRest) {
    // when
    final var command = getCommand(client, useRest).processDefinitionKey(123L).send();

    assertThatThrownBy(command::join)
        .hasMessageContaining("Expected to find process definition with key '123', but none found");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCreateWithStartInstructions(final boolean useRest, final TestInfo testInfo) {
    // given
    deployProcesses(testInfo, useRest);

    // when
    final var instance =
        getCommand(client, useRest)
            .processDefinitionKey(secondProcessDefinitionKey)
            .startBeforeElement("end1")
            .startBeforeElement("end2")
            .send()
            .join();

    final var processInstanceKey = instance.getProcessInstanceKey();

    // then
    assertThat(processInstanceKey).isPositive();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .extracting(Record::getValue)
        .extracting(
            ProcessInstanceRecordValue::getBpmnElementType,
            ProcessInstanceRecordValue::getElementId)
        .describedAs("Expect that both end events are activated")
        .contains(
            tuple(BpmnElementType.END_EVENT, "end1"), tuple(BpmnElementType.END_EVENT, "end2"))
        .describedAs("Expect that the start event is not activated")
        .doesNotContain(tuple(BpmnElementType.START_EVENT, "v2"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true})
  public void shouldRejectCreateWithStartInstructions(
      final boolean useRest, final TestInfo testInfo) {
    // given
    deployProcesses(testInfo, useRest);

    // when
    final var command =
        getCommand(client, useRest)
            .bpmnProcessId(processId2)
            .latestVersion()
            // without variables
            .startBeforeElement("end")
            .send();

    assertThatThrownBy(command::join)
        .hasMessageContaining("[NO_VARIABLE_FOUND] No variable found with name 'missing_var'");
  }

  private CreateProcessInstanceCommandStep1 getCommand(
      final CamundaClient client, final boolean useRest) {
    final CreateProcessInstanceCommandStep1 createInstanceCommand =
        client.newCreateInstanceCommand();
    return useRest ? createInstanceCommand.useRest() : createInstanceCommand.useGrpc();
  }

  private void deployProcesses(final TestInfo testInfo, final boolean useRest) {
    processId = "process-" + testInfo.getTestMethod().get().getName();
    processId2 = processId + "-2";
    firstProcessDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess(processId).startEvent("v1").done(), useRest);
    secondProcessDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess(processId)
                .startEvent("v2")
                .parallelGateway()
                .endEvent("end1")
                .moveToLastGateway()
                .endEvent("end2")
                .done(),
            useRest);

    resourcesHelper.deployProcess(
        Bpmn.createExecutableProcess(processId2)
            .eventSubProcess(
                "event-sub",
                e ->
                    e.startEvent("msg-start-event")
                        .message(msg -> msg.name("msg").zeebeCorrelationKey("=missing_var")))
            .startEvent("v3")
            .endEvent("end")
            .done(),
        useRest);
  }
}
