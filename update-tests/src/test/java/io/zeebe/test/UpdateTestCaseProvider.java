/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.client.api.response.ActivateJobsResponse;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.test.UpdateTestCase.TestCaseBuilder;
import io.zeebe.util.collection.Tuple;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

public class UpdateTestCaseProvider implements ArgumentsProvider {
  static final String PROCESS_ID = "process";
  static final String CHILD_PROCESS_ID = "childProc";
  static final String TASK = "task";
  static final String JOB = TASK;
  static final String MESSAGE = "message";

  @Override
  public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
    return Stream.of(
        scenario()
            .name("job")
            .deployProcess(jobProcess())
            .createInstance()
            .beforeUpgrade(this::activateJob)
            .afterUpgrade(this::completeJob)
            .done(),
        scenario()
            .name("message subscription")
            .deployProcess(messageProcess())
            .createInstance(Map.of("key", "123"))
            .beforeUpgrade(this::awaitOpenMessageSubscription)
            .afterUpgrade(this::publishMessage)
            .done(),
        scenario()
            .name("message start event")
            .deployProcess(msgStartProcess())
            .beforeUpgrade(this::awaitStartMessageSubscription)
            .afterUpgrade(this::publishMessage)
            .done(),
        scenario()
            .name("message event sub-process")
            .deployProcess(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .eventSubProcess(
                        "event-subprocess",
                        eventSubProcess ->
                            eventSubProcess
                                .startEvent()
                                .message(m -> m.name(MESSAGE).zeebeCorrelationKeyExpression("key"))
                                .interrupting(false)
                                .endEvent())
                    .startEvent()
                    .serviceTask(TASK, t -> t.zeebeJobType(TASK))
                    .endEvent()
                    .done())
            .createInstance(Map.of("key", "123"))
            .beforeUpgrade(
                state -> {
                  publishMessage(state, -1L, -1L);
                  awaitElementInState(state, "event-subprocess", "ELEMENT_COMPLETED");

                  return activateJob(state);
                })
            .afterUpgrade(this::completeJob)
            .done(),
        scenario()
            .name("timer")
            .deployProcess(timerProcess())
            .beforeUpgrade(this::awaitTimerCreation)
            .afterUpgrade(this::awaitTimerTriggered)
            .done(),
        scenario()
            .name("incident")
            .deployProcess(incidentProcess())
            .createInstance()
            .beforeUpgrade(this::awaitIncidentCreation)
            .afterUpgrade(this::resolveIncident)
            .done(),
        scenario()
            .name("publish message")
            .deployProcess(messageProcess())
            .beforeUpgrade(
                state -> {
                  publishMessage(state, -1L, -1L);
                  return -1L;
                })
            .afterUpgrade(
                (state, l1, l2) -> {
                  state
                      .client()
                      .newCreateInstanceCommand()
                      .bpmnProcessId(PROCESS_ID)
                      .latestVersion()
                      .variables(Map.of("key", "123"))
                      .send();

                  awaitMessageCorrelation(state, MESSAGE);
                })
            .done(),
        scenario()
            .name("call activity")
            .deployProcess(
                new Tuple<>(parentProcess(), PROCESS_ID),
                new Tuple<>(childProcess(), CHILD_PROCESS_ID))
            .createInstance()
            .afterUpgrade(
                (state, wfKey, key) -> {
                  awaitElementInState(state, JOB, "CREATED");

                  final var jobsResponse =
                      state
                          .client()
                          .newActivateJobsCommand()
                          .jobType(TASK)
                          .maxJobsToActivate(1)
                          .send()
                          .join();
                  assertThat(jobsResponse.getJobs()).hasSize(1);

                  awaitElementInState(state, JOB, "ACTIVATED");
                  state
                      .client()
                      .newCompleteCommand(jobsResponse.getJobs().get(0).getKey())
                      .send()
                      .join();

                  awaitChildProcessCompleted(state, CHILD_PROCESS_ID);
                })
            .done(),
        scenario()
            .name("parallel gateway")
            .deployProcess(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .parallelGateway("fork")
                    .serviceTask(TASK, t -> t.zeebeJobType(TASK))
                    .parallelGateway("join")
                    .moveToNode("fork")
                    .sequenceFlowId("to-join")
                    .connectTo("join")
                    .endEvent()
                    .done())
            .createInstance()
            .beforeUpgrade(this::activateJob)
            .afterUpgrade(this::completeJob)
            .done(),
        scenario()
            .name("exclusive gateway")
            .deployProcess(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .exclusiveGateway()
                    .sequenceFlowId("s1")
                    .conditionExpression("x > 5")
                    .serviceTask(TASK, t -> t.zeebeJobType(TASK))
                    .endEvent()
                    .moveToLastExclusiveGateway()
                    .sequenceFlowId("s2")
                    .defaultFlow()
                    .serviceTask("other-task", t -> t.zeebeJobType("other-task"))
                    .endEvent()
                    .done())
            .createInstance(Map.of("x", 10))
            .beforeUpgrade(this::activateJob)
            .afterUpgrade(this::completeJob)
            .done());
  }

  private BpmnModelInstance jobProcess() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask(TASK, t -> t.zeebeJobType(TASK))
        .endEvent()
        .done();
  }

  private long activateJob(final ContainerState state) {
    awaitElementInState(state, JOB, "CREATED");

    final ActivateJobsResponse jobsResponse =
        state.client().newActivateJobsCommand().jobType(TASK).maxJobsToActivate(1).send().join();
    assertThat(jobsResponse.getJobs()).hasSize(1);

    awaitElementInState(state, JOB, "ACTIVATED");
    return jobsResponse.getJobs().get(0).getKey();
  }

  private void completeJob(final ContainerState state, final long wfInstanceKey, final long key) {
    state.client().newCompleteCommand(key).send().join();
  }

  private BpmnModelInstance messageProcess() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .intermediateCatchEvent(
            "catch", b -> b.message(m -> m.name(MESSAGE).zeebeCorrelationKeyExpression("key")))
        .endEvent()
        .done();
  }

  private void publishMessage(
      final ContainerState state, final long wfInstanceKey, final long key) {
    state
        .client()
        .newPublishMessageCommand()
        .messageName(MESSAGE)
        .correlationKey("123")
        .timeToLive(Duration.ofMinutes(5))
        .variables(Map.of("x", 1))
        .send()
        .join();

    awaitMessageIsInState(state, MESSAGE, "PUBLISHED");
  }

  private BpmnModelInstance msgStartProcess() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .message(b -> b.zeebeCorrelationKeyExpression("key").name(MESSAGE))
        .endEvent()
        .done();
  }

  private BpmnModelInstance timerProcess() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .timerWithCycle("R/PT1S")
        .endEvent()
        .done();
  }

  private BpmnModelInstance incidentProcess() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask("failingTask", t -> t.zeebeJobType(TASK).zeebeInputExpression("foo", "foo"))
        .done();
  }

  private void resolveIncident(
      final ContainerState state, final long wfInstanceKey, final long key) {
    state
        .client()
        .newSetVariablesCommand(wfInstanceKey)
        .variables(Map.of("foo", "bar"))
        .send()
        .join();

    state.client().newResolveIncidentCommand(key).send().join();
    final ActivateJobsResponse job =
        state.client().newActivateJobsCommand().jobType(TASK).maxJobsToActivate(1).send().join();
    state.client().newCompleteCommand(job.getJobs().get(0).getKey()).send().join();
  }

  private BpmnModelInstance parentProcess() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .callActivity("c", b -> b.zeebeProcessId(CHILD_PROCESS_ID))
        .endEvent()
        .done();
  }

  private BpmnModelInstance childProcess() {
    return Bpmn.createExecutableProcess(CHILD_PROCESS_ID)
        .startEvent()
        .serviceTask(TASK, b -> b.zeebeJobType(TASK))
        .endEvent()
        .done();
  }

  private void awaitMessageCorrelation(final ContainerState state, final String message) {
    Awaitility.await(String.format("until message %s is correlated", message))
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> state.hasLogContaining(message, "CORRELATED"));
  }

  private void awaitMessageIsInState(
      final ContainerState state, final String message, final String messageState) {
    Awaitility.await(String.format("until message %s is %s", message, messageState))
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> state.hasMessageInState(message, messageState));
  }

  private long awaitOpenMessageSubscription(final ContainerState state) {
    Awaitility.await("until a message subscription is opened")
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> state.hasLogContaining("MESSAGE_SUBSCRIPTION", "OPENED"));

    return -1L;
  }

  private long awaitStartMessageSubscription(final ContainerState state) {
    Awaitility.await("until a start event message subscription is opened")
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> state.hasLogContaining("MESSAGE_START_EVENT_SUBSCRIPTION", "OPENED"));

    return -1L;
  }

  private long awaitTimerCreation(final ContainerState state) {
    Awaitility.await("until a timer is created")
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> state.hasLogContaining("TIMER", "CREATED"));

    return -1L;
  }

  private void awaitTimerTriggered(
      final ContainerState state, final long wfInstanceKey, final long key) {
    Awaitility.await("until a timer is created")
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> state.hasLogContaining("TIMER", "TRIGGERED"));
  }

  private void awaitChildProcessCompleted(final ContainerState state, final String processId) {
    Awaitility.await("until the child process is completed")
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> state.hasLogContaining(CHILD_PROCESS_ID, "COMPLETED"));
  }

  private long awaitIncidentCreation(final ContainerState state) {
    Awaitility.await("until an incident is created")
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> state.hasLogContaining("INCIDENT", "CREATED"));

    return state.getIncidentKey();
  }

  private void awaitElementInState(
      final ContainerState state, final String elementId, final String elementState) {
    Awaitility.await(String.format("until element %s is in state %s", elementId, elementState))
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> state.hasElementInState(elementId, elementState));
  }

  private TestCaseBuilder scenario() {
    return UpdateTestCase.builder();
  }
}
