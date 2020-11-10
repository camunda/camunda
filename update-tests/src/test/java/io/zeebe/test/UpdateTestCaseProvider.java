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
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.collection.Tuple;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
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
            .deployWorkflow(jobWorkflow())
            .createInstance()
            .beforeUpgrade(this::activateJob)
            .afterUpgrade(this::completeJob)
            .done(),
        scenario()
            .name("message subscription")
            .deployWorkflow(messageWorkflow())
            .createInstance(Map.of("key", "123"))
            .beforeUpgrade(this::awaitOpenMessageSubscription)
            .afterUpgrade(this::publishMessage)
            .done(),
        scenario()
            .name("message start event")
            .deployWorkflow(msgStartWorkflow())
            .beforeUpgrade(this::awaitStartMessageSubscription)
            .afterUpgrade(this::publishMessage)
            .done(),
        scenario()
            .name("message event sub-process")
            .deployWorkflow(
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

                  TestUtil.waitUntil(
                      () -> state.hasElementInState("event-subprocess", "ELEMENT_COMPLETED"));

                  return activateJob(state);
                })
            .afterUpgrade(this::completeJob)
            .done(),
        scenario()
            .name("timer")
            .deployWorkflow(timerWorkflow())
            .beforeUpgrade(this::awaitTimerCreation)
            .afterUpgrade(this::timerTriggered)
            .done(),
        scenario()
            .name("incident")
            .deployWorkflow(incidentWorkflow())
            .createInstance()
            .beforeUpgrade(this::awaitIncidentCreation)
            .afterUpgrade(this::resolveIncident)
            .done(),
        scenario()
            .name("publish message")
            .deployWorkflow(messageWorkflow())
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
                  TestUtil.waitUntil(() -> state.hasLogContaining(MESSAGE, "CORRELATED"));
                })
            .done(),
        scenario()
            .name("call activity")
            .deployWorkflow(
                new Tuple<>(parentWorkflow(), PROCESS_ID),
                new Tuple<>(childWorkflow(), CHILD_PROCESS_ID))
            .createInstance()
            .afterUpgrade(
                (state, wfKey, key) -> {
                  TestUtil.waitUntil(() -> state.hasElementInState(JOB, "CREATED"));

                  final var jobsResponse =
                      state
                          .client()
                          .newActivateJobsCommand()
                          .jobType(TASK)
                          .maxJobsToActivate(1)
                          .send()
                          .join();
                  assertThat(jobsResponse.getJobs()).hasSize(1);

                  TestUtil.waitUntil(() -> state.hasElementInState(JOB, "ACTIVATED"));

                  state
                      .client()
                      .newCompleteCommand(jobsResponse.getJobs().get(0).getKey())
                      .send()
                      .join();
                  TestUtil.waitUntil(() -> state.hasLogContaining(CHILD_PROCESS_ID, "COMPLETED"));
                })
            .done(),
        scenario()
            .name("parallel gateway")
            .deployWorkflow(
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
            .deployWorkflow(
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

  private BpmnModelInstance jobWorkflow() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask(TASK, t -> t.zeebeJobType(TASK))
        .endEvent()
        .done();
  }

  private long activateJob(final ContainerState state) {
    TestUtil.waitUntil(() -> state.hasElementInState(JOB, "CREATED"));

    final ActivateJobsResponse jobsResponse =
        state.client().newActivateJobsCommand().jobType(TASK).maxJobsToActivate(1).send().join();
    assertThat(jobsResponse.getJobs()).hasSize(1);

    TestUtil.waitUntil(() -> state.hasElementInState(JOB, "ACTIVATED"));
    return jobsResponse.getJobs().get(0).getKey();
  }

  private void completeJob(final ContainerState state, final long wfInstanceKey, final long key) {
    state.client().newCompleteCommand(key).send().join();
  }

  private BpmnModelInstance messageWorkflow() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .intermediateCatchEvent(
            "catch", b -> b.message(m -> m.name(MESSAGE).zeebeCorrelationKeyExpression("key")))
        .endEvent()
        .done();
  }

  private long awaitOpenMessageSubscription(final ContainerState state) {
    TestUtil.waitUntil(() -> state.hasLogContaining("MESSAGE_SUBSCRIPTION", "OPENED"));
    return -1L;
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

    TestUtil.waitUntil(() -> state.hasMessageInState(MESSAGE, "PUBLISHED"));
  }

  private BpmnModelInstance msgStartWorkflow() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .message(b -> b.zeebeCorrelationKeyExpression("key").name(MESSAGE))
        .endEvent()
        .done();
  }

  private long awaitStartMessageSubscription(final ContainerState state) {
    TestUtil.waitUntil(() -> state.hasLogContaining("MESSAGE_START_EVENT_SUBSCRIPTION", "OPENED"));
    return -1L;
  }

  private BpmnModelInstance timerWorkflow() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .timerWithCycle("R/PT1S")
        .endEvent()
        .done();
  }

  private long awaitTimerCreation(final ContainerState state) {
    TestUtil.waitUntil(() -> state.hasLogContaining("TIMER", "CREATED"));
    return -1L;
  }

  private void timerTriggered(
      final ContainerState state, final long wfInstanceKey, final long key) {
    TestUtil.waitUntil(() -> state.hasLogContaining("TIMER", "TRIGGERED"));
  }

  private BpmnModelInstance incidentWorkflow() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask("failingTask", t -> t.zeebeJobType(TASK).zeebeInputExpression("foo", "foo"))
        .done();
  }

  private long awaitIncidentCreation(final ContainerState state) {
    TestUtil.waitUntil(() -> state.hasLogContaining("INCIDENT", "CREATED"));
    return state.getIncidentKey();
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

  private BpmnModelInstance parentWorkflow() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .callActivity("c", b -> b.zeebeProcessId(CHILD_PROCESS_ID))
        .endEvent()
        .done();
  }

  private BpmnModelInstance childWorkflow() {
    return Bpmn.createExecutableProcess(CHILD_PROCESS_ID)
        .startEvent()
        .serviceTask(TASK, b -> b.zeebeJobType(TASK))
        .endEvent()
        .done();
  }

  private TestCaseBuilder scenario() {
    return UpdateTestCase.builder();
  }
}
