/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.spring.utils.DatabaseTypeUtils.CAMUNDA_DATABASE_TYPE_NONE;
import static io.camunda.spring.utils.DatabaseTypeUtils.UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.LookupUserTaskResponse;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * End-to-end tests of {@link CamundaClient#newLookupUserTaskCommand(long)} against a real broker,
 * through the REST API ({@code POST /v2/user-tasks/{userTaskKey}/lookup}).
 *
 * <p>The broker runs without secondary storage on purpose: the lookup reads the task and its
 * variables from the engine's state, so every test here also proves it works headless. Task keys
 * come from the recording exporter, since without secondary storage there is no user task search.
 *
 * <p>The max message size is lowered to 64 KB so that a few variables are enough to exceed it. The
 * other tests only send small payloads, so the limit doesn't affect them.
 */
@ZeebeIntegration
final class UserTaskLookupIT {

  private static final DataSize MAX_MESSAGE_SIZE = DataSize.ofKilobytes(64);

  @TestZeebe(purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withUnauthenticatedAccess()
          // Making sure it works without secondary storage / reads from primary
          .withProperty(UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE, CAMUNDA_DATABASE_TYPE_NONE)
          .withRecordingExporter(true)
          .withClusterConfig(c -> c.getNetwork().setMaxMessageSize(MAX_MESSAGE_SIZE));

  private static CamundaClient client;

  @BeforeAll
  static void setUp() {
    client = BROKER.newClientBuilder().build();
  }

  @AfterAll
  static void tearDown() {
    client.close();
  }

  @Test
  void shouldReadNextTaskWithVariablesOfCompletedTask() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    deploy(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .userTask("review")
            .zeebeUserTask()
            .userTask("approve")
            .zeebeUserTask()
            .endEvent()
            .done());
    final long processInstanceKey = createInstance(processId, Map.of("amount", 100));
    final long reviewTaskKey = awaitUserTaskKey(processInstanceKey, "review");

    // when
    client
        .newCompleteUserTaskCommand(reviewTaskKey)
        .variables(Map.of("reviewed", true, "amount", 120))
        .send()
        .join();
    final long approveTaskKey = awaitUserTaskKey(processInstanceKey, "approve");
    final var approveTask = client.newLookupUserTaskCommand(approveTaskKey).send().join();

    // then
    assertThat(approveTask.getUserTaskKey()).isEqualTo(approveTaskKey);
    assertThat(approveTask.getElementId()).isEqualTo("approve");
    assertThat(approveTask.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(approveTask.getBpmnProcessId()).isEqualTo(processId);
    assertThat(approveTask.getState()).isEqualTo(UserTaskState.CREATED);
    assertThat(approveTask.getVariablesAsMap())
        .containsOnly(entry("reviewed", true), entry("amount", 120));
  }

  @Test
  void shouldReturnOnlyRequestedVariables() {
    // given
    final long userTaskKey = createTaskWithShadowedVariable();

    // when
    final var task =
        client.newLookupUserTaskCommand(userTaskKey).fetchVariables("shadowed").send().join();

    // then
    assertThat(task.getVariablesAsMap()).containsOnly(entry("shadowed", "inner"));
  }

  @Test
  void shouldReturnEffectiveVariablesWhenNoneRequested() {
    // given
    final long userTaskKey = createTaskWithShadowedVariable();

    // when
    final var task = client.newLookupUserTaskCommand(userTaskKey).send().join();

    // then
    assertThat(task.getVariablesAsMap())
        .containsOnly(entry("outerOnly", "outer"), entry("shadowed", "inner"));
  }

  @Test
  void shouldReturnNotFoundOnceTaskIsCompleted() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    deploy(singleUserTaskProcess(processId));
    final long userTaskKey = awaitUserTaskKey(createInstance(processId, Map.of()), "task");
    final Supplier<LookupUserTaskResponse> lookupUserTask =
        () -> client.newLookupUserTaskCommand(userTaskKey).send().join();
    // making sure the task is there before completion
    assertThat(lookupUserTask.get().getUserTaskKey()).isEqualTo(userTaskKey);

    // when
    client.newCompleteUserTaskCommand(userTaskKey).send().join();

    // then
    final var problem =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(lookupUserTask::get).actual();
    assertThat(problem.code()).isEqualTo(404);
    assertThat(problem.details().getDetail())
        .isEqualTo(
            "Command 'FETCH' rejected with code 'NOT_FOUND': Expected to fetch user task with key"
                + " '%d', but no such user task was found".formatted(userTaskKey));
  }

  @Test
  void shouldRejectOversizedLookupAndStillCompleteTask() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    deploy(singleUserTaskProcess(processId));
    final long processInstanceKey = createInstance(processId, Map.of("small", "value"));
    final long userTaskKey = awaitUserTaskKey(processInstanceKey, "task");
    // together 76 KB, more than the 64 KB max message size MAX_MESSAGE_SIZE. Set one by one: the
    // records written for one variable update carry its value twice, so each value must stay under
    // half the limit
    setVariable(processInstanceKey, "first", kilobytes(22));
    setVariable(processInstanceKey, "second", kilobytes(20));
    setVariable(processInstanceKey, "third", kilobytes(18));
    setVariable(processInstanceKey, "fourth", kilobytes(16));

    // when
    final var problem =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(() -> client.newLookupUserTaskCommand(userTaskKey).send().join())
            .actual();

    // then
    assertThat(problem.code()).isEqualTo(400);
    assertThat(problem.details().getDetail())
        .isEqualTo(
            ("Command 'FETCH' rejected with code 'INVALID_ARGUMENT': Expected to fetch user task"
                    + " with key '%d', but with 77KB the response is larger than the max message"
                    + " size of 64KB. Narrow fetchVariables to the variables you need, or read the"
                    + " variables from the user task search. Variables in the response: first"
                    + " (~22KB), second (~20KB), third (~18KB), fourth (~16KB), small (~6B)")
                .formatted(userTaskKey));

    assertThat(
            client
                .newLookupUserTaskCommand(userTaskKey)
                .fetchVariables("small")
                .send()
                .join()
                .getVariablesAsMap())
        .containsOnly(entry("small", "value"));
    client.newCompleteUserTaskCommand(userTaskKey).send().join();
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.COMPLETED)
                .withRecordKey(userTaskKey)
                .exists())
        .isTrue();
  }

  @Test
  void shouldReportClaimInProgressAsAssigning() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var listenerType = Strings.newRandomValidBpmnId();
    deploy(
        singleUserTaskProcessWithListener(
            processId, ZeebeTaskListenerEventType.assigning, listenerType));
    final long processInstanceKey = createInstance(processId, Map.of());
    final long userTaskKey = awaitUserTaskKey(processInstanceKey, "task");

    // when
    final var claim =
        client
            .newAssignUserTaskCommand(userTaskKey)
            .assignee("claimer")
            .allowOverride(false)
            .send();
    final long listenerJobKey = awaitListenerJobKey(processInstanceKey, listenerType);
    final var duringClaim = client.newLookupUserTaskCommand(userTaskKey).send().join();
    client.newCompleteCommand(listenerJobKey).send().join();
    claim.join();
    final var afterClaim = client.newLookupUserTaskCommand(userTaskKey).send().join();

    // then
    assertThat(duringClaim.getState()).isEqualTo(UserTaskState.ASSIGNING);
    assertThat(duringClaim.getAssignee())
        .describedAs("the claim is applied only once the listener completes")
        .isNull();
    assertThat(afterClaim.getState()).isEqualTo(UserTaskState.CREATED);
    assertThat(afterClaim.getAssignee()).isEqualTo("claimer");
  }

  @Test
  void shouldNotReturnCompletionVariablesWhileCompleting() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var listenerType = Strings.newRandomValidBpmnId();
    deploy(
        singleUserTaskProcessWithListener(
            processId, ZeebeTaskListenerEventType.completing, listenerType));
    final long processInstanceKey = createInstance(processId, Map.of("var", "committed"));
    final long userTaskKey = awaitUserTaskKey(processInstanceKey, "task");

    // when
    final var completion =
        client.newCompleteUserTaskCommand(userTaskKey).variables(Map.of("var", "pending")).send();
    final long listenerJobKey = awaitListenerJobKey(processInstanceKey, listenerType);
    final var duringCompletion = client.newLookupUserTaskCommand(userTaskKey).send().join();
    client.newCompleteCommand(listenerJobKey).send().join();
    completion.join();

    // then
    assertThat(duringCompletion.getState()).isEqualTo(UserTaskState.COMPLETING);
    assertThat(duringCompletion.getVariablesAsMap())
        .describedAs("the completion's variables are applied only once the listener completes")
        .containsOnly(entry("var", "committed"));
  }

  /**
   * The task sits in a subprocess that overrides {@code shadowed} with an input mapping, so the
   * task sees {@code shadowed = "inner"} and the process-level {@code outerOnly}.
   */
  private static long createTaskWithShadowedVariable() {
    final var processId = Strings.newRandomValidBpmnId();
    deploy(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                "sub",
                s ->
                    s.zeebeInputExpression("\"inner\"", "shadowed")
                        .embeddedSubProcess()
                        .startEvent()
                        .userTask("task")
                        .zeebeUserTask()
                        .endEvent())
            .endEvent()
            .done());
    final long processInstanceKey =
        createInstance(processId, Map.of("outerOnly", "outer", "shadowed", "outer"));
    return awaitUserTaskKey(processInstanceKey, "task");
  }

  private static BpmnModelInstance singleUserTaskProcess(final String processId) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .userTask("task")
        .zeebeUserTask()
        .endEvent()
        .done();
  }

  /** The listener's job is never picked up by a worker, so it holds the task until completed. */
  private static BpmnModelInstance singleUserTaskProcessWithListener(
      final String processId,
      final ZeebeTaskListenerEventType eventType,
      final String listenerType) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .userTask(
            "task",
            t ->
                t.zeebeUserTask().zeebeTaskListener(l -> l.eventType(eventType).type(listenerType)))
        .endEvent()
        .done();
  }

  private static void deploy(final BpmnModelInstance process) {
    client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();
  }

  private static long createInstance(final String processId, final Map<String, Object> variables) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(variables)
        .send()
        .join()
        .getProcessInstanceKey();
  }

  private static long awaitUserTaskKey(final long processInstanceKey, final String elementId) {
    return RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(elementId)
        .getFirst()
        .getKey();
  }

  /** The listener job is created once the engine has moved the task into the held state. */
  private static long awaitListenerJobKey(
      final long processInstanceKey, final String listenerType) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(listenerType)
        .getFirst()
        .getKey();
  }

  private static void setVariable(
      final long processInstanceKey, final String name, final String value) {
    client.newSetVariablesCommand(processInstanceKey).variables(Map.of(name, value)).send().join();
  }

  private static String kilobytes(final int kilobytes) {
    return "x".repeat(kilobytes * 1024);
  }
}
