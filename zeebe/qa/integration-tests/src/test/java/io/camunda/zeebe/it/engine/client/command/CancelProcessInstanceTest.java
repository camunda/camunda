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
import io.camunda.client.api.command.CancelProcessInstanceCommandStep1;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.grpc.Status.Code;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ZeebeIntegration
public final class CancelProcessInstanceTest {

  private static final String PROCESS_ID = "process";
  @AutoClose CamundaClient client;

  @TestZeebe
  final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

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
  public void shouldCancelProcessInstance(final boolean useRest) {
    // given
    final long processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    // when
    getCommand(client, useRest, processInstanceKey).send().join();

    // then
    ZeebeAssertHelper.assertProcessInstanceCanceled(PROCESS_ID);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectIfProcessInstanceIsEnded(final boolean useRest) {
    // given
    final long processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    getCommand(client, useRest, processInstanceKey).send().join();

    // when
    final var command = getCommand(client, useRest, processInstanceKey).send();

    // then
    if (useRest) {
      assertThatThrownBy(command::join)
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining(
              String.format(
                  "Expected to cancel a process instance with key '%s', but no such process was found",
                  processInstanceKey));
    } else {
      assertThatThrownBy(command::join)
          .isInstanceOf(ClientStatusException.class)
          .extracting("status.code")
          .isEqualTo(Code.NOT_FOUND);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectIfNotProcessInstanceKey(final boolean useRest) {
    // given
    final long processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    final long elementInstanceKey =
        RecordingExporter.jobRecords()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue()
            .getElementInstanceKey();

    // when
    final var command = getCommand(client, useRest, elementInstanceKey).send();

    // then
    if (useRest) {
      assertThatThrownBy(command::join)
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining(
              String.format(
                  "Expected to cancel a process instance with key '%s', but no such process was found",
                  elementInstanceKey));
    } else {
      assertThatThrownBy(command::join)
          .isInstanceOf(ClientStatusException.class)
          .extracting("status.code")
          .isEqualTo(Code.NOT_FOUND);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectIfProcessInstanceIsTerminating(final boolean useRest) {
    // given - the termination waits for a canceling task listener job
    final var listenerType = Strings.newRandomValidBpmnId();
    final long userTaskKey =
        resourcesHelper.createSingleUserTask(
            t -> t.zeebeTaskListener(l -> l.canceling().type(listenerType)));
    final long processInstanceKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withRecordKey(userTaskKey)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    final var firstCancel = getCommand(client, useRest, processInstanceKey).send();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(listenerType)
        .await();

    // when
    final var command = getCommand(client, useRest, processInstanceKey).send();

    // then
    if (useRest) {
      assertThatThrownBy(command::join)
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining(
              String.format(
                  "Expected to cancel a process instance with key '%s', but a cancel request is already in progress",
                  processInstanceKey))
          .satisfies(e -> assertThat(((ProblemException) e).code()).isEqualTo(409));
    } else {
      assertThatThrownBy(command::join)
          .isInstanceOf(ClientStatusException.class)
          .extracting("status.code")
          .isEqualTo(Code.FAILED_PRECONDITION);
    }

    // cleanup - complete the listener job so the process instance terminates
    client
        .newActivateJobsCommand()
        .jobType(listenerType)
        .maxJobsToActivate(1)
        .send()
        .join()
        .getJobs()
        .forEach(job -> client.newCompleteCommand(job).send().join());
    firstCancel.join();
  }

  private CancelProcessInstanceCommandStep1 getCommand(
      final CamundaClient client, final boolean useRest, final long processIntanceKey) {
    final CancelProcessInstanceCommandStep1 cancelCommand =
        client.newCancelInstanceCommand(processIntanceKey);
    return useRest ? cancelCommand.useRest() : cancelCommand.useGrpc();
  }
}
