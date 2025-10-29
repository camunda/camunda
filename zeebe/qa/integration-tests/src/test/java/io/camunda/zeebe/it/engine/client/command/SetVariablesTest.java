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
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.SetVariablesCommandStep1;
import io.camunda.zeebe.client.api.response.SetVariablesResponse;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ZeebeIntegration
@AutoCloseResources
public final class SetVariablesTest {

  private static final String PROCESS_ID = "process";

  @AutoCloseResource ZeebeClient client;

  @TestZeebe
  final TestStandaloneBroker zeebe = new TestStandaloneBroker().withRecordingExporter(true);

  ZeebeResourcesHelper resourcesHelper;
  private long processDefinitionKey;

  @BeforeEach
  public void init() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
    processDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .done());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldSetVariables(final boolean useRest) {
    // given
    final long processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    // when
    getCommand(client, useRest, processInstanceKey).variables(Map.of("foo", "bar")).send().join();

    // then
    ZeebeAssertHelper.assertVariableDocumentUpdated(
        (variableDocument) ->
            assertThat(variableDocument.getVariables()).containsOnly(entry("foo", "bar")));

    final Record<VariableDocumentRecordValue> record =
        RecordingExporter.variableDocumentRecords(VariableDocumentIntent.UPDATED).getFirst();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldSetVariablesWithNullVariables(final boolean useRest) {
    // given
    final long processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    // when
    final ZeebeFuture<SetVariablesResponse> command =
        getCommand(client, useRest, processInstanceKey).variables("null").send();

    // then
    if (useRest) {
      assertThatThrownBy(command::join).hasMessageContaining("No variables provided.");
    } else {
      command.join();
      ZeebeAssertHelper.assertVariableDocumentUpdated(
          (variableDocument) -> assertThat(variableDocument.getVariables()).isEmpty());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectIfVariablesAreInvalid(final boolean useRest) {
    // given
    final long processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    // then
    if (useRest) {
      assertThatThrownBy(
              () -> getCommand(client, useRest, processInstanceKey).variables("[]").send().join())
          .hasMessageContaining("Failed to deserialize json '[]' to 'Map<String, Object>");
    } else {
      assertThatThrownBy(
              () -> getCommand(client, useRest, processInstanceKey).variables("[]").send().join())
          .hasMessageContaining(
              "Property 'variables' is invalid: Expected document to be a root level object, but was 'ARRAY'");
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectIfProcessInstanceIsEnded(final boolean useRest) {
    // given
    final long processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    client.newCancelInstanceCommand(processInstanceKey).send().join();

    // when
    final var command =
        getCommand(client, useRest, processInstanceKey).variables(Map.of("foo", "bar")).send();

    // then
    final var expectedMessage =
        String.format(
            "Expected to update variables for element with key '%d', but no such element was found",
            processInstanceKey);

    assertThatThrownBy(command::join).hasMessageContaining(expectedMessage);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectIfPartitionNotFound(final boolean useRest) {
    // when
    final int processInstanceKey = 0;
    final var command =
        getCommand(client, useRest, processInstanceKey)
            .variables(Map.of("foo", "bar"))
            .requestTimeout(Duration.ofSeconds(60))
            .send();

    // then
    if (useRest) {
      final String expectedMessage =
          "Expected to handle REST API request, but request could not be delivered";
      assertThatThrownBy(command::join).hasMessageContaining(expectedMessage);
    } else {
      final String expectedMessage =
          "Expected to execute command on partition 0, but either it does not exist, or the gateway is not yet aware of it";
      assertThatThrownBy(command::join).hasMessageContaining(expectedMessage);
    }
  }

  private SetVariablesCommandStep1 getCommand(
      final ZeebeClient client, final boolean useRest, final long elementInstanceKey) {
    final SetVariablesCommandStep1 setVariablesCommand =
        client.newSetVariablesCommand(elementInstanceKey);
    return useRest ? setVariablesCommand.useRest() : setVariablesCommand.useGrpc();
  }
}
