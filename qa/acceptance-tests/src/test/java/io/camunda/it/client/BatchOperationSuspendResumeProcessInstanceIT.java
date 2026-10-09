/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForBatchOperationCompleted;
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static io.camunda.it.util.TestHelper.waitForProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToBeSuspended;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class BatchOperationSuspendResumeProcessInstanceIT {

  private static CamundaClient camundaClient;

  @Test
  void shouldSuspendOnlyChildInstanceWhenFilteringByParentProcessInstanceKey() {
    // given - a child created through a call activity and an unrelated root of the same definition
    final var scenario = startParentWithChildAndUnrelatedRoot();

    // when
    final var batchOperationKey =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceSuspend()
            .filter(
                f ->
                    f.processDefinitionId(scenario.childProcessId())
                        .parentProcessInstanceKey(scenario.parentKey()))
            .send()
            .join()
            .getBatchOperationKey();

    // then
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, 1);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, 1, 0);
    waitForProcessInstancesToBeSuspended(
        camundaClient, f -> f.processInstanceKey(scenario.childKey()), 1);
    assertThat(getState(scenario.unrelatedRootKey())).isEqualTo(ProcessInstanceState.ACTIVE);
  }

  @Test
  void shouldResumeOnlyChildInstanceWhenFilteringByParentProcessInstanceKey() {
    // given - child and unrelated root are both suspended
    final var scenario = startParentWithChildAndUnrelatedRoot();
    camundaClient.newSuspendProcessInstanceCommand(scenario.childKey()).send().join();
    camundaClient.newSuspendProcessInstanceCommand(scenario.unrelatedRootKey()).send().join();
    waitForProcessInstancesToBeSuspended(
        camundaClient,
        f -> f.processInstanceKey(k -> k.in(scenario.childKey(), scenario.unrelatedRootKey())),
        2);

    // when
    final var batchOperationKey =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceResume()
            .filter(
                f ->
                    f.processDefinitionId(scenario.childProcessId())
                        .parentProcessInstanceKey(scenario.parentKey()))
            .send()
            .join()
            .getBatchOperationKey();

    // then
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, 1);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, 1, 0);
    waitForProcessInstance(
        camundaClient,
        f -> f.processInstanceKey(scenario.childKey()),
        instances ->
            assertThat(instances)
                .singleElement()
                .extracting(ProcessInstance::getState)
                .isEqualTo(ProcessInstanceState.ACTIVE));
    assertThat(getState(scenario.unrelatedRootKey())).isEqualTo(ProcessInstanceState.SUSPENDED);
  }

  @Test
  void shouldRejectSuspendWithTopLevelStateOtherThanActive() {
    // given
    final long processInstanceKey = startActiveRootInstance();

    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newCreateBatchOperationCommand()
                    .processInstanceSuspend()
                    .filter(
                        f ->
                            f.processInstanceKey(processInstanceKey)
                                .state(ProcessInstanceState.COMPLETED))
                    .send()
                    .join())
        .isInstanceOfSatisfying(
            ProblemException.class,
            e -> {
              assertThat(e.code()).isEqualTo(400);
              assertThat(e.getMessage())
                  .contains("The value for state is 'COMPLETED' but must be one of [ACTIVE]");
            });
    assertThat(getState(processInstanceKey)).isEqualTo(ProcessInstanceState.ACTIVE);
  }

  @Test
  void shouldRejectResumeWithTopLevelStateOtherThanSuspended() {
    // given
    final long processInstanceKey = startActiveRootInstance();
    camundaClient.newSuspendProcessInstanceCommand(processInstanceKey).send().join();
    waitForProcessInstancesToBeSuspended(
        camundaClient, f -> f.processInstanceKey(processInstanceKey), 1);

    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newCreateBatchOperationCommand()
                    .processInstanceResume()
                    .filter(
                        f ->
                            f.processInstanceKey(processInstanceKey)
                                .state(ProcessInstanceState.ACTIVE))
                    .send()
                    .join())
        .isInstanceOfSatisfying(
            ProblemException.class,
            e -> {
              assertThat(e.code()).isEqualTo(400);
              assertThat(e.getMessage())
                  .contains("The value for state is 'ACTIVE' but must be one of [SUSPENDED]");
            });
    assertThat(getState(processInstanceKey)).isEqualTo(ProcessInstanceState.SUSPENDED);
  }

  @Test
  void shouldSuspendOnlyMatchingInstancesWhenOrFilterHasNeverMatchingBranch() {
    // given
    final long matchingKey = startActiveRootInstance();
    final long otherKey = startActiveRootInstance();

    // when - the COMPLETED branch can never match a suspendable instance
    final var batchOperationKey =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceSuspend()
            .filter(
                f ->
                    f.orFilters(
                        List.of(
                            b -> b.state(ProcessInstanceState.COMPLETED),
                            b -> b.processInstanceKey(matchingKey))))
            .send()
            .join()
            .getBatchOperationKey();

    // then
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, 1);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, 1, 0);
    waitForProcessInstancesToBeSuspended(camundaClient, f -> f.processInstanceKey(matchingKey), 1);
    assertThat(getState(otherKey)).isEqualTo(ProcessInstanceState.ACTIVE);
  }

  @Test
  void shouldResumeOnlyMatchingInstancesWhenOrFilterHasNeverMatchingBranch() {
    // given
    final long matchingKey = startActiveRootInstance();
    final long otherKey = startActiveRootInstance();
    camundaClient.newSuspendProcessInstanceCommand(matchingKey).send().join();
    camundaClient.newSuspendProcessInstanceCommand(otherKey).send().join();
    waitForProcessInstancesToBeSuspended(
        camundaClient, f -> f.processInstanceKey(k -> k.in(matchingKey, otherKey)), 2);

    // when - the ACTIVE branch can never match a resumable instance
    final var batchOperationKey =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceResume()
            .filter(
                f ->
                    f.orFilters(
                        List.of(
                            b -> b.state(ProcessInstanceState.ACTIVE),
                            b -> b.processInstanceKey(matchingKey))))
            .send()
            .join()
            .getBatchOperationKey();

    // then
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, 1);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, 1, 0);
    waitForProcessInstance(
        camundaClient,
        f -> f.processInstanceKey(matchingKey),
        instances ->
            assertThat(instances)
                .singleElement()
                .extracting(ProcessInstance::getState)
                .isEqualTo(ProcessInstanceState.ACTIVE));
    assertThat(getState(otherKey)).isEqualTo(ProcessInstanceState.SUSPENDED);
  }

  private static ProcessInstanceState getState(final long processInstanceKey) {
    return camundaClient.newProcessInstanceGetRequest(processInstanceKey).send().join().getState();
  }

  private record ParentChildScenario(
      String childProcessId, long parentKey, long childKey, long unrelatedRootKey) {}

  private static ParentChildScenario startParentWithChildAndUnrelatedRoot() {
    final var childProcessId = Strings.newRandomValidBpmnId();
    final var parentProcessId = Strings.newRandomValidBpmnId();
    final var jobType = Strings.newRandomValidBpmnId();

    final var child =
        Bpmn.createExecutableProcess(childProcessId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();
    final var parent =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId(childProcessId))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, child, childProcessId + ".bpmn");
    deployProcessAndWaitForIt(camundaClient, parent, parentProcessId + ".bpmn");

    final long parentKey =
        startProcessInstance(camundaClient, parentProcessId).getProcessInstanceKey();
    final long unrelatedRootKey =
        startProcessInstance(camundaClient, childProcessId).getProcessInstanceKey();
    waitForProcessInstancesToStart(
        camundaClient, f -> f.processInstanceKey(k -> k.in(parentKey, unrelatedRootKey)), 2);

    final var childKey = new AtomicLong();
    Awaitility.await("child process instance is exported to the search index")
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var items =
                  camundaClient
                      .newProcessInstanceSearchRequest()
                      .filter(f -> f.parentProcessInstanceKey(parentKey))
                      .send()
                      .join()
                      .items();
              assertThat(items).hasSize(1);
              childKey.set(items.getFirst().getProcessInstanceKey());
            });
    return new ParentChildScenario(childProcessId, parentKey, childKey.get(), unrelatedRootKey);
  }

  private static long startActiveRootInstance() {
    final var processId = Strings.newRandomValidBpmnId();
    final var jobType = Strings.newRandomValidBpmnId();
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();

    deployProcessAndWaitForIt(camundaClient, process, processId + ".bpmn");
    final long processInstanceKey =
        startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    waitForProcessInstancesToStart(camundaClient, f -> f.processInstanceKey(processInstanceKey), 1);
    return processInstanceKey;
  }
}
