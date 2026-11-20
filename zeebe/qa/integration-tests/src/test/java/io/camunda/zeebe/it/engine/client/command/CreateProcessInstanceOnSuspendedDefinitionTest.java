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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceCreationHelper;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.grpc.Status.Code;
import java.time.Duration;
import org.apache.http.HttpStatus;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks that REST and gRPC callers get the engine's rejection when creating an instance on a
 * suspended process definition version. Runs on a single partition, because suspension is not
 * distributed to other partitions yet: with more partitions, a creation could land on one where the
 * version isn't suspended.
 */
@ZeebeIntegration
final class CreateProcessInstanceOnSuspendedDefinitionTest {

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose CamundaClient client;
  ZeebeResourcesHelper resourcesHelper;
  private String processId;

  @BeforeEach
  void init(final TestInfo testInfo) {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
    processId = "process-" + testInfo.getTestMethod().orElseThrow().getName();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldRejectCreateByKeyWhenSuspended(final boolean useRest) {
    // given
    final long key = deployAndSuspend(useRest);

    // when / then
    assertRejectedAsSuspended(
        useRest, () -> getCommand(useRest).processDefinitionKey(key).send().join(), key);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldRejectCreateByVersionWhenSuspended(final boolean useRest) {
    // given
    final long key = deployAndSuspend(useRest);

    // when / then
    assertRejectedAsSuspended(
        useRest, () -> getCommand(useRest).bpmnProcessId(processId).version(1).send().join(), key);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldRejectCreateWithResultWhenSuspended(final boolean useRest) {
    // given
    final long key = deployAndSuspend(useRest);

    // when / then
    assertRejectedAsSuspended(
        useRest,
        () -> getCommand(useRest).processDefinitionKey(key).withResult().send().join(),
        key);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldRejectCreateWithStartInstructionsWhenSuspended(final boolean useRest) {
    // given
    final long key = deployAndSuspend(useRest);

    // when / then
    assertRejectedAsSuspended(
        useRest,
        () -> getCommand(useRest).processDefinitionKey(key).startBeforeElement("end").send().join(),
        key);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldCreateOnLatestActiveVersionWhenLatestIsSuspended(final boolean useRest) {
    // given
    final long v1 = deploy(useRest, "end-v1");
    final long v2 = deploy(useRest, "end-v2");
    suspend(v2);

    // when
    final var instance = getCommand(useRest).bpmnProcessId(processId).latestVersion().send().join();

    // then
    assertThat(instance.getProcessDefinitionKey()).isEqualTo(v1);
    assertThat(instance.getVersion()).isEqualTo(1);
  }

  private long deployAndSuspend(final boolean useRest) {
    final long key = deploy(useRest, "end");
    suspend(key);
    return key;
  }

  private long deploy(final boolean useRest, final String endEventId) {
    return resourcesHelper.deployProcess(
        Bpmn.createExecutableProcess(processId).startEvent().endEvent(endEventId).done(), useRest);
  }

  private void suspend(final long processDefinitionKey) {
    client
        .newSuspendProcessDefinitionCommand()
        .processDefinitionKey(processDefinitionKey)
        .send()
        .join();
  }

  private CreateProcessInstanceCommandStep1 getCommand(final boolean useRest) {
    final CreateProcessInstanceCommandStep1 command = client.newCreateInstanceCommand();
    return useRest ? command.useRest() : command.useGrpc();
  }

  private void assertRejectedAsSuspended(
      final boolean useRest, final ThrowingCallable create, final long key) {
    final var reason =
        ProcessInstanceCreationHelper.ERROR_MESSAGE_PROCESS_IS_SUSPENDED.formatted(
            processId, 1, key);
    if (useRest) {
      assertThatThrownBy(create)
          .isInstanceOfSatisfying(
              ProblemException.class,
              ex -> {
                assertThat(ex.details().getStatus()).isEqualTo(HttpStatus.SC_CONFLICT);
                assertThat(ex.details().getTitle()).isEqualTo("INVALID_STATE");
                assertThat(ex.details().getDetail()).contains(reason);
              });
    } else {
      assertThatThrownBy(create)
          .isInstanceOfSatisfying(
              ClientStatusException.class,
              ex -> {
                assertThat(ex.getStatusCode()).isEqualTo(Code.FAILED_PRECONDITION);
                assertThat(ex.getMessage()).contains(reason);
              });
    }
  }
}
