/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToBeCompleted;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToBeSuspended;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class ProcessInstanceSuspendResumeIT {

  // element ids are fixed; job types are randomized per test so that jobs cannot be activated by
  // a worker in another test sharing this MultiDb broker, which would starve this test's instances
  private static final String[] ELEMENT_IDS = {"taskA", "taskB", "taskC"};

  private static CamundaClient camundaClient;

  @Test
  void shouldSuspendAndResumeAndCompleteAsIfNeverSuspended() {
    // given
    final var process = deployProcess();
    final long suspendedKey =
        startProcessInstance(camundaClient, process.processId()).getProcessInstanceKey();
    final long notSuspendedKey =
        startProcessInstance(camundaClient, process.processId()).getProcessInstanceKey();
    waitForProcessInstancesToStart(
        camundaClient, f -> f.processInstanceKey(b -> b.in(suspendedKey, notSuspendedKey)), 2);

    // when
    camundaClient.newSuspendProcessInstanceCommand(suspendedKey).send().join();

    // then
    waitForProcessInstancesToBeSuspended(camundaClient, f -> f.processInstanceKey(suspendedKey), 1);
    final ProcessInstance suspendedInstance =
        camundaClient.newProcessInstanceGetRequest(suspendedKey).send().join();
    assertThat(suspendedInstance.getState()).isEqualTo(ProcessInstanceState.SUSPENDED);
    assertThat(suspendedInstance.getSuspendedDate()).isNotNull();

    final ProcessInstance notSuspendedInstance =
        camundaClient.newProcessInstanceGetRequest(notSuspendedKey).send().join();
    assertThat(notSuspendedInstance.getState()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(notSuspendedInstance.getSuspendedDate()).isNull();

    // when
    camundaClient.newResumeProcessInstanceCommand(suspendedKey).send().join();

    // then
    waitForProcessInstance(
        camundaClient,
        f -> f.processInstanceKey(suspendedKey),
        instances ->
            assertThat(instances)
                .singleElement()
                .satisfies(
                    instance -> {
                      assertThat(instance.getState()).isEqualTo(ProcessInstanceState.ACTIVE);
                      assertThat(instance.getSuspendedDate()).isNull();
                    }));

    // when - both instances driven through the same three sequential tasks
    for (final String jobType : process.jobTypes()) {
      activateAndCompleteJobs(jobType, 2);
    }

    // then - both complete, with the same elements in the same end state
    waitForProcessInstancesToBeCompleted(
        camundaClient, f -> f.processInstanceKey(b -> b.in(suspendedKey, notSuspendedKey)), 2);
    // Process-instance COMPLETED can be searchable before every element-instance update is
    // exported. Wait until both trees are fully exported and agree rather than comparing once.
    // Require the known service-task ids so equal-but-incomplete lag cannot pass early.
    await()
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var suspendedElements = elementIdsAndStates(suspendedKey);
              final var notSuspendedElements = elementIdsAndStates(notSuspendedKey);
              // start + taskA/B/C + end
              assertThat(suspendedElements).hasSize(ELEMENT_IDS.length + 2);
              assertThat(suspendedElements)
                  .extracting(t -> t.toList().get(0))
                  .contains((Object[]) ELEMENT_IDS);
              assertThat(suspendedElements)
                  .extracting(t -> t.toList().get(1))
                  .containsOnly(ElementInstanceState.COMPLETED);
              assertThat(suspendedElements)
                  .containsExactlyInAnyOrderElementsOf(notSuspendedElements);
            });
  }

  @Test
  void shouldRejectCommandsWhileSuspendedAndAcceptThemOnceResumed() {
    // given - a job activated before suspension
    final var process = deployProcess();
    final long processInstanceKey =
        startProcessInstance(camundaClient, process.processId()).getProcessInstanceKey();
    waitForProcessInstancesToStart(camundaClient, f -> f.processInstanceKey(processInstanceKey), 1);
    final long jobKey = activateJob(process.jobTypes()[0]);

    camundaClient.newSuspendProcessInstanceCommand(processInstanceKey).send().join();
    waitForProcessInstancesToBeSuspended(
        camundaClient, f -> f.processInstanceKey(processInstanceKey), 1);

    // when
    final var problem =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(() -> camundaClient.newCompleteCommand(jobKey).send().join())
            .actual();

    // then
    assertThat(problem.details().getDetail()).contains("is suspended");

    // when
    camundaClient.newResumeProcessInstanceCommand(processInstanceKey).send().join();
    waitForProcessInstance(
        camundaClient,
        f -> f.processInstanceKey(processInstanceKey),
        instances ->
            assertThat(instances)
                .singleElement()
                .extracting(ProcessInstance::getState)
                .isEqualTo(ProcessInstanceState.ACTIVE));

    // then - the same command is now accepted, and the process advances
    camundaClient.newCompleteCommand(jobKey).send().join();
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceSearchRequest()
                            .filter(
                                f ->
                                    f.processInstanceKey(processInstanceKey)
                                        .elementId(ELEMENT_IDS[1])
                                        .state(ElementInstanceState.ACTIVE))
                            .send()
                            .join()
                            .items())
                    .isNotEmpty());

    // leaves an instance active at taskB; cancel it so it doesn't linger on the shared broker
    camundaClient.newCancelInstanceCommand(processInstanceKey).send().join();
  }

  @Test
  void shouldUpdateVariablesWhileSuspended() {
    // given
    final var process = deployProcess();
    final long processInstanceKey =
        startProcessInstance(camundaClient, process.processId(), Map.of("recoverable", "before"))
            .getProcessInstanceKey();
    waitForProcessInstancesToStart(camundaClient, f -> f.processInstanceKey(processInstanceKey), 1);

    camundaClient.newSuspendProcessInstanceCommand(processInstanceKey).send().join();
    waitForProcessInstancesToBeSuspended(
        camundaClient, f -> f.processInstanceKey(processInstanceKey), 1);

    // when - set-variables is accepted on a suspended instance
    assertThatCode(
            () ->
                camundaClient
                    .newSetVariablesCommand(processInstanceKey)
                    .variables(Map.of("recoverable", "after", "added", 1))
                    .send()
                    .join())
        .doesNotThrowAnyException();

    // then - values are exported and the instance stays suspended
    await()
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var variables =
                  camundaClient
                      .newVariableSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey))
                      .send()
                      .join()
                      .items();
              assertThat(variables)
                  .anySatisfy(
                      v -> {
                        assertThat(v.getName()).isEqualTo("recoverable");
                        assertThat(v.getValue()).isEqualTo("\"after\"");
                      });
              assertThat(variables)
                  .anySatisfy(
                      v -> {
                        assertThat(v.getName()).isEqualTo("added");
                        assertThat(v.getValue()).isEqualTo("1");
                      });
            });

    final ProcessInstance stillSuspended =
        camundaClient.newProcessInstanceGetRequest(processInstanceKey).send().join();
    assertThat(stillSuspended.getState()).isEqualTo(ProcessInstanceState.SUSPENDED);

    camundaClient.newCancelInstanceCommand(processInstanceKey).send().join();
  }

  @Test
  void shouldBufferConditionalActivationTriggeredByVariableUpdateUntilResume() {
    // given - waiting on a conditional intermediate catch event that is not yet satisfied
    final var processId = Strings.newRandomValidBpmnId();
    final var catchEventId = "conditional-catch";
    final var afterConditionTaskId = "afterCondition";
    final var jobType = Strings.newRandomValidBpmnId();
    final var model =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .intermediateCatchEvent(catchEventId)
            .condition(c -> c.condition("=x > 10").zeebeVariableEvents("create, update"))
            .serviceTask(afterConditionTaskId, t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(model, processId + ".bpmn")
        .send()
        .join();
    waitForProcessesToBeDeployed(camundaClient, f -> f.processDefinitionId(processId), 1);

    final long processInstanceKey =
        startProcessInstance(camundaClient, processId, Map.of("x", 1)).getProcessInstanceKey();
    waitForProcessInstancesToStart(camundaClient, f -> f.processInstanceKey(processInstanceKey), 1);
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceSearchRequest()
                            .filter(
                                f ->
                                    f.processInstanceKey(processInstanceKey)
                                        .elementId(catchEventId)
                                        .state(ElementInstanceState.ACTIVE))
                            .send()
                            .join()
                            .items())
                    .isNotEmpty());

    camundaClient.newSuspendProcessInstanceCommand(processInstanceKey).send().join();
    waitForProcessInstancesToBeSuspended(
        camundaClient, f -> f.processInstanceKey(processInstanceKey), 1);

    // when - variable update satisfies the condition while suspended
    camundaClient
        .newSetVariablesCommand(processInstanceKey)
        .variables(Map.of("x", 42))
        .send()
        .join();

    // then - variable is updated while the instance stays suspended
    await()
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newVariableSearchRequest()
                            .filter(f -> f.processInstanceKey(processInstanceKey).name("x"))
                            .send()
                            .join()
                            .items())
                    .anySatisfy(v -> assertThat(v.getValue()).isEqualTo("42")));

    assertThat(
            camundaClient.newProcessInstanceGetRequest(processInstanceKey).send().join().getState())
        .isEqualTo(ProcessInstanceState.SUSPENDED);

    // when - resume drains the buffered conditional trigger
    camundaClient.newResumeProcessInstanceCommand(processInstanceKey).send().join();

    // then - token moves past the catch event
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceSearchRequest()
                            .filter(
                                f ->
                                    f.processInstanceKey(processInstanceKey)
                                        .elementId(afterConditionTaskId)
                                        .state(ElementInstanceState.ACTIVE))
                            .send()
                            .join()
                            .items())
                    .isNotEmpty());
    camundaClient.newCancelInstanceCommand(processInstanceKey).send().join();
  }

  private static void activateAndCompleteJobs(final String jobType, final int count) {
    // Complete whatever each poll activates and accumulate toward count, rather than requiring all
    // count jobs at once. The two instances can reach a task moments apart; a poll that grabbed a
    // subset then asserted the full count would leave those jobs locked for the activation timeout,
    // stalling the next poll instead of making progress.
    final Set<Long> completed = new HashSet<>();
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              camundaClient
                  .newActivateJobsCommand()
                  .jobType(jobType)
                  .maxJobsToActivate(count - completed.size())
                  .workerName("test")
                  .timeout(Duration.ofSeconds(10))
                  .send()
                  .join()
                  .getJobs()
                  .forEach(
                      job -> {
                        camundaClient.newCompleteCommand(job.getKey()).send().join();
                        completed.add(job.getKey());
                      });
              assertThat(completed).hasSize(count);
            });
  }

  private static long activateJob(final String jobType) {
    final var jobs = new long[1];
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
              jobs[0] = activated.getFirst().getKey();
            });
    return jobs[0];
  }

  private static List<Tuple> elementIdsAndStates(final long processInstanceKey) {
    return camundaClient
        .newElementInstanceSearchRequest()
        .filter(f -> f.processInstanceKey(processInstanceKey))
        .page(p -> p.limit(100))
        .send()
        .join()
        .items()
        .stream()
        .map(e -> Tuple.tuple(e.getElementId(), e.getState()))
        .toList();
  }

  private static DeployedProcess deployProcess() {
    final var processId = Strings.newRandomValidBpmnId();
    final var jobTypes =
        new String[] {
          Strings.newRandomValidBpmnId(),
          Strings.newRandomValidBpmnId(),
          Strings.newRandomValidBpmnId()
        };

    AbstractFlowNodeBuilder<?, ?> builder = Bpmn.createExecutableProcess(processId).startEvent();
    for (int i = 0; i < ELEMENT_IDS.length; i++) {
      final String jobType = jobTypes[i];
      builder = builder.serviceTask(ELEMENT_IDS[i], t -> t.zeebeJobType(jobType));
    }
    final var model = builder.endEvent().done();

    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(model, processId + ".bpmn")
        .send()
        .join();
    waitForProcessesToBeDeployed(camundaClient, f -> f.processDefinitionId(processId), 1);

    return new DeployedProcess(processId, jobTypes);
  }

  /** A process deployed for a single test method, so no state is shared across tests. */
  private record DeployedProcess(String processId, String[] jobTypes) {}
}
