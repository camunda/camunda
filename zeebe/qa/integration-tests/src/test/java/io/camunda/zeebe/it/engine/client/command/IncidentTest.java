/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static io.camunda.zeebe.protocol.record.intent.IncidentIntent.CREATED;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ResolveIncidentCommandStep1;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ZeebeIntegration
public final class IncidentTest {

  @AutoClose CamundaClient client;

  @TestZeebe
  final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  public void init() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectResolveOnNonExistingIncident(final boolean useRest) {
    // given
    final int partition = resourcesHelper.getPartitions().getFirst();
    final long nonExistingKey = Protocol.encodePartitionId(partition, 123);

    // when
    final var expectedMessage =
        String.format(
            "Expected to resolve incident with key '%d', but no such incident was found",
            nonExistingKey);

    Assertions.assertThatThrownBy(() -> getCommand(client, useRest, nonExistingKey).send().join())
        .hasMessageContaining(expectedMessage);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldResolveIncident(final boolean useRest) {
    // given
    final long processInstanceKey = createProcessInstance();
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(CREATED).getFirst();

    // when
    client.newSetVariablesCommand(processInstanceKey).variables(Map.of("x", 21)).send().join();

    getCommand(client, useRest, incident.getKey()).send().join();

    // then
    ZeebeAssertHelper.assertIncidentResolved();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectDuplicateResolving(final boolean useRest) {
    // given
    final long processInstanceKey = createProcessInstance();
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(CREATED).getFirst();

    // when
    client.newSetVariablesCommand(processInstanceKey).variables(Map.of("x", 21)).send().join();

    getCommand(client, useRest, incident.getKey()).send().join();

    // then
    ZeebeAssertHelper.assertIncidentResolved();

    final var expectedMessage =
        String.format(
            "Expected to resolve incident with key '%d', but no such incident was found",
            incident.getKey());

    Assertions.assertThatThrownBy(
            () -> getCommand(client, useRest, incident.getKey()).send().join())
        .hasMessageContaining(expectedMessage);
  }

  private ResolveIncidentCommandStep1 getCommand(
      final CamundaClient client, final boolean useRest, final long incidentKey) {
    final ResolveIncidentCommandStep1 incidentCommandStep1 =
        client.newResolveIncidentCommand(incidentKey);
    return useRest ? incidentCommandStep1.useRest() : incidentCommandStep1.useGrpc();
  }

  private long createProcessInstance() {
    final long processDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .exclusiveGateway()
                .sequenceFlowId("to-a")
                .conditionExpression("x > 10")
                .endEvent("a")
                .moveToLastExclusiveGateway()
                .sequenceFlowId("to-b")
                .defaultFlow()
                .endEvent("b")
                .done());
    return resourcesHelper.createProcessInstance(processDefinitionKey);
  }
}
