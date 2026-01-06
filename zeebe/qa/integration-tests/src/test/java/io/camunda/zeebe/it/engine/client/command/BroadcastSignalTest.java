/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static io.camunda.zeebe.test.util.record.RecordingExporter.signalRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.BroadcastSignalCommandStep1;
import io.camunda.client.api.response.BroadcastSignalResponse;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.SignalRecordValue;
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
public class BroadcastSignalTest {

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
  public void shouldCreateProcessInstance(final boolean useRest, final TestInfo testInfo) {
    // when
    final var signalName = testInfo.getTestMethod().get().getName();
    deployProcess(signalName);
    getCommand(client, useRest).signalName(signalName).send().join();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementType(BpmnElementType.START_EVENT)
                .withEventType(BpmnEventType.SIGNAL)
                .limit(1)
                .getFirst()
                .getValue())
        .hasElementId("start");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldBroadcastSignalWithoutVariables(
      final boolean useRest, final TestInfo testInfo) {
    // when
    final var signalName = testInfo.getTestMethod().get().getName();
    deployProcess(signalName);
    getCommand(client, useRest).signalName(signalName).send().join();

    // then
    final Record<SignalRecordValue> record =
        signalRecords(SignalIntent.BROADCASTED).withSignalName(signalName).getFirst();
    Assertions.assertThat(record.getValue()).hasSignalName(signalName);

    assertThat(record.getValue().getVariables()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldBroadcastSignalWithVariables(final boolean useRest, final TestInfo testInfo) {
    // when
    final var signalName = testInfo.getTestMethod().get().getName();
    deployProcess(signalName);
    final var variables = Map.of("x", 1, "y", 2);
    getCommand(client, useRest).signalName(signalName).variables(variables).send().join();

    // then
    final Record<SignalRecordValue> record =
        signalRecords(SignalIntent.BROADCASTED).withSignalName(signalName).getFirst();
    Assertions.assertThat(record.getValue()).hasSignalName(signalName);

    assertThat(record.getValue().getVariables()).containsExactlyInAnyOrderEntriesOf(variables);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldBroadcastSignalWithSingleVariable(
      final boolean useRest, final TestInfo testInfo) {
    // when
    final var signalName = testInfo.getTestMethod().get().getName();
    deployProcess(signalName);
    final String key = "key";
    final String value = "value";

    getCommand(client, useRest).signalName(signalName).variable(key, value).send().join();

    // then
    final Record<SignalRecordValue> record =
        signalRecords(SignalIntent.BROADCASTED).withSignalName(signalName).getFirst();
    Assertions.assertThat(record.getValue()).hasSignalName(signalName);

    assertThat(record.getValue().getVariables())
        .containsExactlyInAnyOrderEntriesOf(Map.of(key, value));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldThrowErrorWhenTryToBroadcastSignalWithNullVariable(
      final boolean useRest, final TestInfo testInfo) {
    // when
    final var signalName = testInfo.getTestMethod().get().getName();
    assertThatThrownBy(
            () ->
                getCommand(client, useRest)
                    .signalName(signalName)
                    .variable(null, null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRespondWhenBroadcastingSignal(final boolean useRest, final TestInfo testInfo) {
    // when
    final var signalName = testInfo.getTestMethod().get().getName();
    deployProcess(signalName);
    final CamundaFuture<BroadcastSignalResponse> responseFuture =
        getCommand(client, useRest).signalName(signalName).send();

    final Record<SignalRecordValue> record =
        signalRecords(SignalIntent.BROADCASTED).withSignalName(signalName).getFirst();

    // then
    final BroadcastSignalResponse response = responseFuture.join();
    assertThat(response.getKey()).isEqualTo(record.getKey());
    assertThat(response.getTenantId()).isEqualTo(record.getValue().getTenantId());
  }

  private BroadcastSignalCommandStep1 getCommand(
      final CamundaClient client, final boolean useRest) {
    final BroadcastSignalCommandStep1 broadcastSignalCommand = client.newBroadcastSignalCommand();
    return useRest ? broadcastSignalCommand.useRest() : broadcastSignalCommand.useGrpc();
  }

  private void deployProcess(final String signalName) {
    resourcesHelper.deployProcess(
        Bpmn.createExecutableProcess().startEvent("start").signal(signalName).endEvent().done());
  }
}
