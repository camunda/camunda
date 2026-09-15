/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.WaitStateType;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator.AddTimeRequest;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * End-to-end coverage of timer due-date checks against a live broker while an instance is
 * suspended. EngineRule tests drive time in-process; this class lets {@code
 * DueDateTimerCheckScheduler} race the suspend/resume path on a real cluster clock.
 */
@MultiDbTest
public class ProcessInstanceSuspendResumeTimerIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withProperty("zeebe.clock.controlled", "true");

  private static CamundaClient camundaClient;

  @Test
  void shouldFireTimerTriggeredDuringSuspensionAfterResume() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    deployProcessAndWaitForIt(
        camundaClient,
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .intermediateCatchEvent("timer", e -> e.timerWithDuration("PT5S"))
            .endEvent()
            .done(),
        processId + ".bpmn");
    final long processInstanceKey =
        startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    waitForElementInstances(
        camundaClient,
        f ->
            f.processInstanceKey(processInstanceKey)
                .elementId("timer")
                .state(ElementInstanceState.ACTIVE),
        1);

    camundaClient.newSuspendProcessInstanceCommand(processInstanceKey).send().join();
    waitForProcessInstancesToBeSuspended(
        camundaClient, f -> f.processInstanceKey(processInstanceKey), 1);

    // when - the due-date checker would fire if the timer were still indexed
    addTime(Duration.ofSeconds(10));

    // trigger is buffered: the catch event stays active and the timer wait-state is not
    // removed (TRIGGERED would drop it)
    assertTimerWaitStatePresent(processInstanceKey);

    // when
    camundaClient.newResumeProcessInstanceCommand(processInstanceKey).send().join();

    // then
    waitForProcessInstancesToBeCompleted(
        camundaClient, f -> f.processInstanceKey(processInstanceKey), 1);
    assertNumberOfCompletedTimers(processInstanceKey, "timer", 1);
  }

  @Test
  void shouldFireTimerThatBecomesDueAfterResume() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    deployProcessAndWaitForIt(
        camundaClient,
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .intermediateCatchEvent("timer", e -> e.timerWithDuration("PT5S"))
            .endEvent()
            .done(),
        processId + ".bpmn");
    final long processInstanceKey =
        startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    waitForElementInstances(
        camundaClient,
        f ->
            f.processInstanceKey(processInstanceKey)
                .elementId("timer")
                .state(ElementInstanceState.ACTIVE),
        1);

    camundaClient.newSuspendProcessInstanceCommand(processInstanceKey).send().join();
    waitForProcessInstancesToBeSuspended(
        camundaClient, f -> f.processInstanceKey(processInstanceKey), 1);

    // when - resume before the timer is due, then let the checker fire normally
    camundaClient.newResumeProcessInstanceCommand(processInstanceKey).send().join();
    waitForProcessInstancesToStart(camundaClient, f -> f.processInstanceKey(processInstanceKey), 1);
    waitForElementInstances(
        camundaClient,
        f ->
            f.processInstanceKey(processInstanceKey)
                .elementId("timer")
                .state(ElementInstanceState.ACTIVE),
        1);
    // the catch event is active again, but the timer must not fire until it is due
    assertTimerWaitStatePresent(processInstanceKey);

    addTime(Duration.ofSeconds(10));

    // then
    waitForProcessInstancesToBeCompleted(
        camundaClient, f -> f.processInstanceKey(processInstanceKey), 1);
    assertNumberOfCompletedTimers(processInstanceKey, "timer", 1);
  }

  /**
   * When a cycling timer triggers during a suspension, we expect only a single trigger to be
   * buffered. Following this, upon resumption, if next due date of the timer is in the past, the
   * timer will be rescheduled again to now(), causing a second trigger.
   */
  @Test
  void shouldFireCyclingTimerTwiceAfterResumeWhenSeveralCyclesWereDue() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var jobType = Strings.newRandomValidBpmnId();
    deployProcessAndWaitForIt(
        camundaClient,
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .boundaryEvent("timer", b -> b.cancelActivity(false).timerWithCycle("R/PT1M"))
            .endEvent("timerEnd")
            .moveToActivity("task")
            .endEvent("taskEnd")
            .done(),
        processId + ".bpmn");
    final long processInstanceKey =
        startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    waitForElementInstances(
        camundaClient,
        f ->
            f.processInstanceKey(processInstanceKey)
                .elementId("task")
                .state(ElementInstanceState.ACTIVE),
        1);

    camundaClient.newSuspendProcessInstanceCommand(processInstanceKey).send().join();
    waitForProcessInstancesToBeSuspended(
        camundaClient, f -> f.processInstanceKey(processInstanceKey), 1);

    // when - ten one-minute dues pass while suspended; the checker must not catch up yet
    addTime(Duration.ofMinutes(10));
    waitForElementInstances(
        camundaClient,
        f ->
            f.processInstanceKey(processInstanceKey)
                .elementId("task")
                .state(ElementInstanceState.ACTIVE),
        1);
    // boundary timers are not wait-states; assert the catch-up path did not complete timerEnd
    await()
        .pollDelay(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceSearchRequest()
                            .filter(
                                f ->
                                    f.processInstanceKey(processInstanceKey)
                                        .elementId("timerEnd")
                                        .state(ElementInstanceState.COMPLETED))
                            .send()
                            .join()
                            .items())
                    .isEmpty());

    camundaClient.newResumeProcessInstanceCommand(processInstanceKey).send().join();

    // then - buffered trigger plus one overdue reschedule snapped to now; not ten catch-up fires
    waitForElementInstances(
        camundaClient,
        f ->
            f.processInstanceKey(processInstanceKey)
                .elementId("timerEnd")
                .state(ElementInstanceState.COMPLETED),
        2);
    assertNumberOfCompletedTimers(processInstanceKey, "timerEnd", 2);
  }

  private static void assertNumberOfCompletedTimers(
      final long processInstanceKey, final String elementId, final int numberOfCompletedTimers) {
    await()
        .pollDelay(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceSearchRequest()
                            .filter(
                                f ->
                                    f.processInstanceKey(processInstanceKey)
                                        .elementId(elementId)
                                        .state(ElementInstanceState.COMPLETED))
                            .send()
                            .join()
                            .items())
                    .hasSize(numberOfCompletedTimers));
  }

  private static void assertTimerWaitStatePresent(final long processInstanceKey) {
    await()
        .pollDelay(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(
                                f ->
                                    f.processInstanceKey(processInstanceKey)
                                        .waitStateType(WaitStateType.TIMER))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));
  }

  private static void addTime(final Duration duration) {
    ActorClockActuator.of(BROKER.actuatorUri("clock").toString())
        .addTime(new AddTimeRequest(duration.toMillis()));
  }

  private static void completeJob(final String jobType) {
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var activated =
                  camundaClient
                      .newActivateJobsCommand()
                      .jobType(jobType)
                      .maxJobsToActivate(1)
                      .workerName("test")
                      .timeout(Duration.ofSeconds(10))
                      .send()
                      .join()
                      .getJobs();
              assertThat(activated).hasSize(1);
              camundaClient.newCompleteCommand(activated.getFirst().getKey()).send().join();
            });
  }
}
