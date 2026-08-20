/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.waitForProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.AssignProcessInstanceBusinessIdCommandStep1.AssignProcessInstanceBusinessIdCommandStep2;
import io.camunda.client.api.command.ClientException;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@MultiDbTest
class ProcessInstanceBusinessIdAssignmentIT {

  private static CamundaClient camundaClient;

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldAssignBusinessIdToRunningProcessInstance(final boolean useRest) {
    // given - a running process instance that currently has no business id
    final long processInstanceKey = startProcessInstance("business-id-assign", useRest);
    final var businessId = "order-" + transport(useRest) + "-98765";

    // when - the business id is assigned late via the Java client over the chosen transport
    assignBusinessId(processInstanceKey, businessId, useRest).send().join();

    // then - the assignment propagates to secondary storage
    awaitBusinessId(processInstanceKey, businessId);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldRejectAssignmentToNonExistentProcessInstance(final boolean useRest) {
    // given - a key that routes to a real partition but does not identify any instance
    final long nonExistentKey = Protocol.encodePartitionId(Protocol.START_PARTITION_ID, 9_999_999L);

    // when / then
    assertThatThrownBy(
            () -> assignBusinessId(nonExistentKey, "order-missing", useRest).send().join())
        .describedAs("assigning a business id to a non-existent process instance is rejected")
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("no such process instance was found");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldRejectEmptyBusinessId(final boolean useRest) {
    // given - a running process instance
    final long processInstanceKey = startProcessInstance("business-id-empty", useRest);

    // when / then - an empty business id is rejected early at the gateway on both transports (REST
    // by the request validator, gRPC by the request mapper), so neither reaches the broker.
    final var expectedMessage = useRest ? "No businessId provided" : "no business id was provided";
    assertThatThrownBy(() -> assignBusinessId(processInstanceKey, "", useRest).send().join())
        .describedAs("assigning an empty business id is rejected over %s", transport(useRest))
        .isInstanceOf(ClientException.class)
        .hasMessageContaining(expectedMessage);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldRejectReassigningBusinessId(final boolean useRest) {
    // given - a process instance that already has a business id assigned
    final long processInstanceKey = startProcessInstance("business-id-reassign", useRest);
    assignBusinessId(processInstanceKey, "order-original", useRest).send().join();

    // when / then - reassigning is rejected regardless of whether the id differs or is identical
    assertThatThrownBy(
            () -> assignBusinessId(processInstanceKey, "order-different", useRest).send().join())
        .describedAs(
            "assigning a different business id to an already-assigned instance is rejected")
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("it already has a business id assigned");

    assertThatThrownBy(
            () -> assignBusinessId(processInstanceKey, "order-original", useRest).send().join())
        .describedAs(
            "re-sending the identical business id to an already-assigned instance is rejected")
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("it already has a business id assigned");
  }

  private AssignProcessInstanceBusinessIdCommandStep2 assignBusinessId(
      final long processInstanceKey, final String businessId, final boolean useRest) {
    final var command = camundaClient.newAssignProcessInstanceBusinessIdCommand(processInstanceKey);
    return (useRest ? command.useRest() : command.useGrpc()).businessId(businessId);
  }

  private long startProcessInstance(final String prefix, final boolean useRest) {
    final var processId = prefix + "-" + transport(useRest) + "-process";
    final var processDefinition =
        Bpmn.createExecutableProcess(processId).startEvent().userTask("task").endEvent().done();
    deployProcessAndWaitForIt(camundaClient, processDefinition, processId + ".bpmn");

    final long processInstanceKey =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    waitForProcessInstance(
        camundaClient,
        f -> f.processInstanceKey(processInstanceKey),
        instances -> assertThat(instances).hasSize(1));

    return processInstanceKey;
  }

  private void awaitBusinessId(final long processInstanceKey, final String businessId) {
    await("business id is reflected in secondary storage")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newProcessInstanceGetRequest(processInstanceKey)
                            .send()
                            .join()
                            .getBusinessId())
                    .isEqualTo(businessId));
  }

  private static String transport(final boolean useRest) {
    return useRest ? "rest" : "grpc";
  }
}
