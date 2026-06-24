/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.getScopedVariables;
import static io.camunda.it.util.TestHelper.startScopedProcessInstance;
import static io.camunda.it.util.TestHelper.waitForActiveScopedUserTasks;
import static io.camunda.it.util.TestHelper.waitForBatchOperationCompleted;
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitForScopedProcessInstancesToStart;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CreateBatchOperationCommandStep1.CreateBatchOperationCommandStep3;
import io.camunda.client.api.command.CreateBatchOperationCommandStep1.ProcessInstanceModificationStep;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.response.BatchOperationItems.BatchOperationItem;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class BatchOperationModifyProcessInstanceIT {

  private static CamundaClient camundaClient;
  String testScopeId;

  final List<Process> deployedProcesses = new ArrayList<>();
  final List<Long> processInstancesPath1 = new ArrayList<>();
  final List<Long> processInstancesPath2 = new ArrayList<>();

  @BeforeEach
  public void beforeEach(final TestInfo testInfo) {
    Objects.requireNonNull(camundaClient);
    testScopeId =
        testInfo.getTestMethod().map(Method::toString).orElse(UUID.randomUUID().toString());

    Objects.requireNonNull(camundaClient);
    final List<String> processes =
        List.of("multi_instance_subprocess.bpmn", "service_tasks_v1.bpmn");
    processes.forEach(
        process ->
            deployedProcesses.addAll(
                deployResource(camundaClient, String.format("process/%s", process))
                    .getProcesses()));

    waitForProcessesToBeDeployed(camundaClient, deployedProcesses.size());

    // start some random process instances we want to not match the filter
    IntStream.range(0, 10)
        .forEach(i -> startScopedProcessInstance(camundaClient, "service_tasks_v1", testScopeId));

    // start some process instances for one path
    IntStream.range(0, 10)
        .forEach(
            i -> {
              final var processInstance =
                  startScopedProcessInstance(
                      camundaClient,
                      "multi_instance_subprocess",
                      testScopeId,
                      Map.of("variables", Set.of("foo", "bar"), "foo", 1));
              processInstancesPath1.add(processInstance.getProcessInstanceKey());
            });

    // start some process instances for another path
    IntStream.range(0, 10)
        .forEach(
            i -> {
              final var processInstance =
                  startScopedProcessInstance(
                      camundaClient,
                      "multi_instance_subprocess",
                      testScopeId,
                      Map.of("variables", Set.of("foo", "bar"), "foo", 2));
              processInstancesPath2.add(processInstance.getProcessInstanceKey());
            });

    waitForScopedProcessInstancesToStart(camundaClient, testScopeId, 30);
    final var expectedUserTasks =
        (processInstancesPath1.size() * 2) + 10 + (processInstancesPath2.size() * 2) + 10;
    waitForActiveScopedUserTasks(camundaClient, testScopeId, expectedUserTasks);
  }

  @AfterEach
  void afterEach() {
    testScopeId = null;
    deployedProcesses.clear();
    processInstancesPath1.clear();
    processInstancesPath2.clear();
  }

  @Test
  void shouldModifyProcessInstancesWithBatch() {
    final var allProcessInstances = new ArrayList<Long>();
    allProcessInstances.addAll(processInstancesPath1);
    allProcessInstances.addAll(processInstancesPath2);

    // when
    final var batchOperationKey =
        modifyProcessInstance(
            b ->
                b.addMoveInstruction("userTaskE", "userTaskF")
                    .filter(
                        f ->
                            f.variables(getScopedVariables(testScopeId))
                                .processDefinitionId("multi_instance_subprocess")));

    // then wait if batch has correct amount of items. (To fail fast if not)
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, 20);

    // and
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, 20, 0);
    batchOperationHasItemsWithState(
        batchOperationKey, BatchOperationItemState.COMPLETED, allProcessInstances);

    for (final long itemKey : allProcessInstances) {
      processInstanceHasActiveUserTasks(camundaClient, itemKey, Map.of("userTaskF", 2L));
    }
  }

  @Test
  void shouldModifyProcessInstancesWithMultipleBatches() {
    // given
    final var processInstanceKey1 = processInstancesPath1.get(0);
    final var processInstanceKey2 = processInstancesPath1.get(1);

    // when
    final var batchOperationKey1 =
        modifyProcessInstance(
            b ->
                b.addMoveInstruction("userTaskE", "userTaskF")
                    .filter(
                        f ->
                            f.variables(getScopedVariables(testScopeId))
                                .processInstanceKey(processInstanceKey1)));
    final var batchOperationKey2 =
        modifyProcessInstance(
            b ->
                b.addMoveInstruction("userTaskC", "userTaskD")
                    .filter(
                        f ->
                            f.variables(getScopedVariables(testScopeId))
                                .processInstanceKey(processInstanceKey1)));
    final var batchOperationKey3 =
        modifyProcessInstance(
            b ->
                b.addMoveInstruction("userTaskE", "userTaskF")
                    .filter(
                        f ->
                            f.variables(getScopedVariables(testScopeId))
                                .processInstanceKey(processInstanceKey2)));

    // then wait if batch has correct amount of items. (If not, fail fast)
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey1, 1);
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey2, 1);
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey3, 1);

    // and
    waitForBatchOperationCompleted(camundaClient, batchOperationKey1, 1, 0);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey2, 1, 0);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey3, 1, 0);

    batchOperationHasItemsWithState(
        batchOperationKey1, BatchOperationItemState.COMPLETED, List.of(processInstanceKey1));
    batchOperationHasItemsWithState(
        batchOperationKey1, BatchOperationItemState.COMPLETED, List.of(processInstanceKey1));
    batchOperationHasItemsWithState(
        batchOperationKey1, BatchOperationItemState.COMPLETED, List.of(processInstanceKey2));

    processInstanceHasActiveUserTasks(
        camundaClient, processInstanceKey1, Map.of("userTaskD", 1L, "userTaskF", 2L));
    processInstanceHasActiveUserTasks(camundaClient, processInstanceKey2, Map.of("userTaskF", 2L));
  }

  @Test
  void shouldModifyProcessInstancesWithBatchFail() {
    // when
    final var batchOperationKey =
        modifyProcessInstance(
            b ->
                b.addMoveInstruction("userTaskA", "userTaskB")
                    .addMoveInstruction("userTaskC", "FAIL")
                    .addMoveInstruction("userTaskE", "userTaskF")
                    .filter(
                        f ->
                            f.variables(getScopedVariables(testScopeId))
                                .processDefinitionId("multi_instance_subprocess")));

    // then
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, 20);

    // and
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, 10, 10);
    batchOperationHasItemsWithState(
        batchOperationKey, BatchOperationItemState.FAILED, processInstancesPath1);
    batchOperationHasItemsWithState(
        batchOperationKey, BatchOperationItemState.COMPLETED, processInstancesPath2);

    // and
    for (final long itemKey : processInstancesPath2) {
      processInstanceHasActiveUserTasks(
          camundaClient,
          itemKey,
          Map.of(
              "userTaskB", 1L,
              "userTaskF", 2L));
    }
  }

  public String modifyProcessInstance(
      final Function<
              ProcessInstanceModificationStep<ProcessInstanceFilter>,
              CreateBatchOperationCommandStep3<ProcessInstanceFilter>>
          commandBuilder) {
    return commandBuilder
        .apply(camundaClient.newCreateBatchOperationCommand().modifyProcessInstance())
        .send()
        .join()
        .getBatchOperationKey();
  }

  public void batchOperationHasItemsWithState(
      final String batchOperationKey,
      final BatchOperationItemState state,
      final List<Long> failedItemKeys) {
    await("should have items with state")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .pollInterval(Duration.ofMillis(100))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var itemsObj =
                  camundaClient
                      .newBatchOperationItemsSearchRequest()
                      .filter(f -> f.batchOperationKey(batchOperationKey))
                      .send()
                      .join();

              final var filteredItems =
                  itemsObj.items().stream().filter(i -> i.getStatus() == state).toList();
              assertThat(filteredItems).hasSize(failedItemKeys.size());
              assertThat(filteredItems.stream().map(BatchOperationItem::getItemKey))
                  .hasSize(failedItemKeys.size());
            });
  }

  public void processInstanceHasActiveUserTasks(
      final CamundaClient client,
      final Long processInstanceKey,
      final Map<String, Long> userTaskCounts) {
    flowNodeInstanceExistAndMatches(
        client,
        f ->
            f.processInstanceKey(processInstanceKey)
                .state(ElementInstanceState.ACTIVE)
                .type(ElementInstanceType.USER_TASK),
        f -> {
          final var elementIdCounts =
              f.stream()
                  .collect(
                      Collectors.groupingBy(ElementInstance::getElementId, Collectors.counting()));
          assertThat(elementIdCounts).containsAllEntriesOf(userTaskCounts);
        });
  }

  public void flowNodeInstanceExistAndMatches(
      final CamundaClient client,
      final Consumer<ElementInstanceFilter> filter,
      final Consumer<List<ElementInstance>> asserter) {
    await()
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var result =
                  client.newElementInstanceSearchRequest().filter(filter).send().join().items();
              asserter.accept(result);
            });
  }
}
