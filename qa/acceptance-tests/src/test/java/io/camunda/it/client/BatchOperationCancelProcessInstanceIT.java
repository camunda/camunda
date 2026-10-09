/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.getScopedVariables;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.startScopedProcessInstance;
import static io.camunda.it.util.TestHelper.waitForBatchOperationCompleted;
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static io.camunda.it.util.TestHelper.waitForProcessInstanceToBeTerminated;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToBeSuspended;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitForScopedProcessInstancesToStart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.BatchOperationItems.BatchOperationItem;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.test.util.Strings;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

@MultiDbTest
public class BatchOperationCancelProcessInstanceIT {

  private static CamundaClient camundaClient;

  String testScopeId;

  final List<Process> deployedProcesses = new ArrayList<>();
  final List<ProcessInstanceEvent> activeProcessInstances = new ArrayList<>();

  @BeforeEach
  public void beforeEach(final TestInfo testInfo) {
    Objects.requireNonNull(camundaClient);
    testScopeId =
        testInfo.getTestMethod().map(Method::toString).orElse(UUID.randomUUID().toString());

    final List<String> processes = List.of("service_tasks_v1.bpmn", "service_tasks_v2.bpmn");

    processes.forEach(
        process ->
            deployedProcesses.addAll(
                deployResource(camundaClient, String.format("process/%s", process))
                    .getProcesses()));

    waitForProcessesToBeDeployed(camundaClient, deployedProcesses.size());

    activeProcessInstances.add(
        startScopedProcessInstance(
            camundaClient, "service_tasks_v1", testScopeId, Map.of("xyz", "bar")));
    activeProcessInstances.add(
        startScopedProcessInstance(
            camundaClient, "service_tasks_v2", testScopeId, Map.of("path", "222")));

    waitForScopedProcessInstancesToStart(camundaClient, testScopeId, activeProcessInstances.size());
  }

  @AfterEach
  void afterEach() {
    deployedProcesses.clear();
    activeProcessInstances.clear();
  }

  @Test
  void shouldCancelProcessInstancesWithBatch() {
    // when
    final var result =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(b -> b.variables(getScopedVariables(testScopeId)))
            .send()
            .join();
    final var batchOperationKey = result.getBatchOperationKey();

    // then
    assertThat(result).isNotNull();

    // and wait if batch has correct amount of items. (To fail fast if not)
    waitForBatchOperationWithCorrectTotalCount(
        camundaClient, batchOperationKey, activeProcessInstances.size());

    // and
    waitForBatchOperationCompleted(
        camundaClient, batchOperationKey, activeProcessInstances.size(), 0);

    // and
    final var activeKeys =
        activeProcessInstances.stream().map(ProcessInstanceEvent::getProcessInstanceKey).toList();
    for (final Long key : activeKeys) {
      waitForProcessInstanceToBeTerminated(camundaClient, key);
    }

    // and
    final var itemsObj =
        camundaClient
            .newBatchOperationItemsSearchRequest()
            .filter(f -> f.batchOperationKey(batchOperationKey))
            .send()
            .join();
    final var itemKeys = itemsObj.items().stream().map(BatchOperationItem::getItemKey).toList();

    assertThat(itemsObj.items()).hasSize(activeProcessInstances.size());
    assertThat(itemsObj.items().stream().map(BatchOperationItem::getStatus).distinct().toList())
        .containsExactly(BatchOperationItemState.COMPLETED);
    assertThat(itemKeys).containsExactlyInAnyOrder(activeKeys.toArray(Long[]::new));
  }

  @Test
  void shouldCancelSuspendedProcessInstancesWithBatch() {
    final List<Long> activeProcessInstanceKeys =
        activeProcessInstances.stream().map(ProcessInstanceEvent::getProcessInstanceKey).toList();

    // given
    camundaClient
        .newCreateBatchOperationCommand()
        .processInstanceSuspend()
        .filter(f -> f.processInstanceKey(k -> k.in(activeProcessInstanceKeys)))
        .send()
        .join();
    waitForProcessInstancesToBeSuspended(
        camundaClient,
        f -> f.variables(getScopedVariables(testScopeId)),
        activeProcessInstances.size());

    // when
    final var result =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(b -> b.variables(getScopedVariables(testScopeId)))
            .send()
            .join();
    final var batchOperationKey = result.getBatchOperationKey();

    // then
    assertThat(result).isNotNull();

    waitForBatchOperationWithCorrectTotalCount(
        camundaClient, batchOperationKey, activeProcessInstances.size());
    waitForBatchOperationCompleted(
        camundaClient, batchOperationKey, activeProcessInstances.size(), 0);

    for (final Long key : activeProcessInstanceKeys) {
      waitForProcessInstanceToBeTerminated(camundaClient, key);
    }

    final var itemsObj =
        camundaClient
            .newBatchOperationItemsSearchRequest()
            .filter(f -> f.batchOperationKey(batchOperationKey))
            .send()
            .join();
    final var itemKeys = itemsObj.items().stream().map(BatchOperationItem::getItemKey).toList();

    assertThat(itemsObj.items()).hasSize(activeProcessInstances.size());
    assertThat(itemsObj.items().stream().map(BatchOperationItem::getStatus).distinct().toList())
        .containsExactly(BatchOperationItemState.COMPLETED);
    assertThat(itemKeys).containsExactlyInAnyOrder(activeProcessInstanceKeys.toArray(Long[]::new));
  }

  @Test
  void shouldNotCancelActiveInstancesWhenFilteringForSuspended() {
    // given
    final long suspendedKey = activeProcessInstances.get(0).getProcessInstanceKey();
    final long activeKey = activeProcessInstances.get(1).getProcessInstanceKey();
    camundaClient.newSuspendProcessInstanceCommand(suspendedKey).send().join();
    waitForProcessInstancesToBeSuspended(camundaClient, f -> f.processInstanceKey(suspendedKey), 1);

    // when
    final var batchOperationKey =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(
                f ->
                    f.variables(getScopedVariables(testScopeId))
                        .state(ProcessInstanceState.SUSPENDED))
            .send()
            .join()
            .getBatchOperationKey();

    // then
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, 1);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, 1, 0);
    waitForProcessInstanceToBeTerminated(camundaClient, suspendedKey);

    final var itemKeys = getBatchItemKeys(batchOperationKey);
    assertThat(itemKeys).containsExactly(suspendedKey);
    assertThat(camundaClient.newProcessInstanceGetRequest(activeKey).send().join().getState())
        .isEqualTo(ProcessInstanceState.ACTIVE);
  }

  @Test
  void shouldNotCancelSuspendedInstancesWhenFilteringForActive() {
    // given
    final long suspendedKey = activeProcessInstances.get(0).getProcessInstanceKey();
    final long activeKey = activeProcessInstances.get(1).getProcessInstanceKey();
    camundaClient.newSuspendProcessInstanceCommand(suspendedKey).send().join();
    waitForProcessInstancesToBeSuspended(camundaClient, f -> f.processInstanceKey(suspendedKey), 1);

    // when
    final var batchOperationKey =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(
                f ->
                    f.variables(getScopedVariables(testScopeId)).state(ProcessInstanceState.ACTIVE))
            .send()
            .join()
            .getBatchOperationKey();

    // then
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, 1);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, 1, 0);
    waitForProcessInstanceToBeTerminated(camundaClient, activeKey);

    final var itemKeys = getBatchItemKeys(batchOperationKey);
    assertThat(itemKeys).containsExactly(activeKey);
    assertThat(camundaClient.newProcessInstanceGetRequest(suspendedKey).send().join().getState())
        .isEqualTo(ProcessInstanceState.SUSPENDED);
  }

  @Test
  void shouldRejectCancellationForNonCancellableState() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newCreateBatchOperationCommand()
                    .processInstanceCancel()
                    .filter(
                        f ->
                            f.variables(getScopedVariables(testScopeId))
                                .state(ProcessInstanceState.COMPLETED))
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("The value for state is 'COMPLETED' but must be one of");
  }

  @Test
  void shouldSelectNoItemsWhenFilteringByParentProcessInstanceKeyOfRootInstance() {
    // given - a child created through a call activity and an unrelated root of the same definition
    final var childProcessId = Strings.newRandomValidBpmnId();
    final var parentProcessId = Strings.newRandomValidBpmnId();
    final var child =
        Bpmn.createExecutableProcess(childProcessId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(Strings.newRandomValidBpmnId()))
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

    // when - only root instances are cancellable, so a parent key filter can match nothing
    final var batchOperationKey =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(f -> f.processDefinitionId(childProcessId).parentProcessInstanceKey(parentKey))
            .send()
            .join()
            .getBatchOperationKey();

    // then
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, 0);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, 0, 0);
    assertThat(getBatchItemKeys(batchOperationKey)).isEmpty();
    assertThat(
            camundaClient.newProcessInstanceGetRequest(unrelatedRootKey).send().join().getState())
        .isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(camundaClient.newProcessInstanceGetRequest(childKey.get()).send().join().getState())
        .isEqualTo(ProcessInstanceState.ACTIVE);
  }

  @Test
  void shouldAcceptOrFilterWithNeverMatchingStateBranchAndOnlyCancelMatchingInstances() {
    // given
    final var activeKeys =
        activeProcessInstances.stream().map(ProcessInstanceEvent::getProcessInstanceKey).toList();
    final long unrelatedKey =
        startScopedProcessInstance(
                camundaClient, "service_tasks_v1", UUID.randomUUID().toString(), Map.of())
            .getProcessInstanceKey();

    // when - the COMPLETED branch can never match a cancellable instance
    final var batchOperationKey =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(
                f ->
                    f.orFilters(
                        List.of(
                            b -> b.state(ProcessInstanceState.COMPLETED),
                            b -> b.processInstanceKey(k -> k.in(activeKeys)))))
            .send()
            .join()
            .getBatchOperationKey();

    // then
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, activeKeys.size());
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, activeKeys.size(), 0);
    for (final Long key : activeKeys) {
      waitForProcessInstanceToBeTerminated(camundaClient, key);
    }
    assertThat(getBatchItemKeys(batchOperationKey))
        .containsExactlyInAnyOrder(activeKeys.toArray(Long[]::new));
    assertThat(camundaClient.newProcessInstanceGetRequest(unrelatedKey).send().join().getState())
        .isEqualTo(ProcessInstanceState.ACTIVE);
  }

  private List<Long> getBatchItemKeys(final String batchOperationKey) {
    return camundaClient
        .newBatchOperationItemsSearchRequest()
        .filter(f -> f.batchOperationKey(batchOperationKey))
        .send()
        .join()
        .items()
        .stream()
        .map(BatchOperationItem::getItemKey)
        .toList();
  }
}
