/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.filter;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CreateBatchOperationCommandStep1.CreateBatchOperationCommandStep3;
import io.camunda.client.api.command.CreateBatchOperationCommandStep1.ProcessInstanceModificationStep;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.response.BatchOperationItems.BatchOperationItem;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "es")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "os")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class BatchOperationModifyProcessInstanceTest {

  static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();
  static final List<Long> PROCESS_INSTANCES_PATH1 = new ArrayList<>();
  static final List<Long> PROCESS_INSTANCES_PATH2 = new ArrayList<>();

  private static CamundaClient camundaClient;

  @BeforeAll
  public static void beforeAll() {
    Objects.requireNonNull(camundaClient);
    final List<String> processes =
        List.of("multi_instance_subprocess.bpmn", "service_tasks_v1.bpmn");
    processes.forEach(
        process ->
            DEPLOYED_PROCESSES.addAll(
                deployResource(camundaClient, String.format("process/%s", process))
                    .getProcesses()));

    waitForProcessesToBeDeployed(camundaClient, DEPLOYED_PROCESSES.size());

    // start some random process instances we want to not match the filter
    IntStream.range(0, 10).forEach(i -> startProcessInstance(camundaClient, "service_tasks_v1"));

    // start some process instances for one path
    IntStream.range(0, 10)
        .forEach(
            i -> {
              final var processInstance =
                  startProcessInstance(
                      camundaClient,
                      "multi_instance_subprocess",
                      "{\"variables\": [\"foo\", \"bar\"], \"foo\": 1}");
              PROCESS_INSTANCES_PATH1.add(processInstance.getProcessInstanceKey());
            });

    // start some process instances for another path
    IntStream.range(0, 10)
        .forEach(
            i -> {
              final var processInstance =
                  startProcessInstance(
                      camundaClient,
                      "multi_instance_subprocess",
                      "{\"variables\": [\"foo\", \"bar\"], \"foo\": 2}");
              PROCESS_INSTANCES_PATH2.add(processInstance.getProcessInstanceKey());
            });

    waitForProcessInstancesToStart(camundaClient, 30);
  }

  @AfterAll
  static void afterAll() {
    DEPLOYED_PROCESSES.clear();
    PROCESS_INSTANCES_PATH1.clear();
    PROCESS_INSTANCES_PATH2.clear();
  }

  @Test
  void shouldModifyProcessInstancesWithBatch() {
    final var allProcessInstances = new ArrayList<Long>();
    allProcessInstances.addAll(PROCESS_INSTANCES_PATH1);
    allProcessInstances.addAll(PROCESS_INSTANCES_PATH2);

    // when
    final var batchOperationKey =
        modifyProcessInstance(
            b ->
                b.addMoveInstruction("userTaskE", "userTaskF")
                    .filter(f -> f.processDefinitionId("multi_instance_subprocess")));
    // then
    shouldCompleteBatchOperation(batchOperationKey);
    batchOperationHasItemsWithState(
        batchOperationKey, BatchOperationItemState.COMPLETED, allProcessInstances);

    for (final long itemKey : allProcessInstances) {
      processInstanceHasActiveUserTasks(camundaClient, itemKey, Map.of("userTaskF", 2L));
    }
  }

  @Test
  void shouldModifyProcessInstancesWithMultipleBatches() {
    // given
    final var processInstanceKey1 = PROCESS_INSTANCES_PATH1.get(0);
    final var processInstanceKey2 = PROCESS_INSTANCES_PATH1.get(1);

    // when
    final var batchOperationKey1 =
        modifyProcessInstance(
            b ->
                b.addMoveInstruction("userTaskE", "userTaskF")
                    .filter(f -> f.processInstanceKey(processInstanceKey1)));
    final var batchOperationKey2 =
        modifyProcessInstance(
            b ->
                b.addMoveInstruction("userTaskC", "userTaskD")
                    .filter(f -> f.processInstanceKey(processInstanceKey1)));
    final var batchOperationKey3 =
        modifyProcessInstance(
            b ->
                b.addMoveInstruction("userTaskE", "userTaskF")
                    .filter(f -> f.processInstanceKey(processInstanceKey2)));

    // then
    shouldCompleteBatchOperation(batchOperationKey1);
    shouldCompleteBatchOperation(batchOperationKey2);
    shouldCompleteBatchOperation(batchOperationKey3);

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
                    .filter(f -> f.processDefinitionId("multi_instance_subprocess")));

    // then
    shouldCompleteBatchOperation(batchOperationKey);
    batchOperationHasItemsWithState(
        batchOperationKey, BatchOperationItemState.FAILED, PROCESS_INSTANCES_PATH1);
    batchOperationHasItemsWithState(
        batchOperationKey, BatchOperationItemState.COMPLETED, PROCESS_INSTANCES_PATH2);

    // and
    for (final long itemKey : PROCESS_INSTANCES_PATH2) {
      processInstanceHasActiveUserTasks(
          camundaClient,
          itemKey,
          Map.of(
              "userTaskB", 1L,
              "userTaskF", 2L));
    }
  }

  public long modifyProcessInstance(
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

  public void shouldCompleteBatchOperation(final long batchOperationKey) {
    await("should complete batch operation")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .pollInterval(Duration.ofMillis(100))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              // and
              final var batch =
                  camundaClient.newBatchOperationGetRequest(batchOperationKey).send().join();
              assertThat(batch).isNotNull();
              assertThat(batch.getEndDate()).isNotNull();
              assertThat(batch.getStatus()).isEqualTo(BatchOperationState.COMPLETED);
            });
  }

  public void batchOperationHasItemsWithState(
      final long batchOperationKey,
      final BatchOperationItemState state,
      final List<Long> failedItemKeys) {
    final var itemsObj =
        camundaClient
            .newBatchOperationItemsSearchRequest()
            .filter(f -> f.batchOperationId(Long.toString(batchOperationKey)))
            .send()
            .join();

    final var filteredItems =
        itemsObj.items().stream().filter(i -> i.getStatus() == state).toList();
    assertThat(filteredItems).hasSize(failedItemKeys.size());
    assertThat(filteredItems.stream().map(BatchOperationItem::getItemKey))
        .hasSize(failedItemKeys.size());
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
