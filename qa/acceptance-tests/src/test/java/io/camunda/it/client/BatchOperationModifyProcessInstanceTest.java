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
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.response.BatchOperationItems.BatchOperationItem;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.impl.search.filter.ProcessInstanceFilterImpl;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
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
  static long processInstanceKey;

  private static CamundaClient camundaClient;

  @BeforeAll
  public static void beforeAll() {
    Objects.requireNonNull(camundaClient);
    final List<String> processes = List.of("multi_instance_subprocess.bpmn");
    processes.forEach(
        process ->
            DEPLOYED_PROCESSES.addAll(
                deployResource(camundaClient, String.format("process/%s", process))
                    .getProcesses()));

    waitForProcessesToBeDeployed(camundaClient, DEPLOYED_PROCESSES.size());

    processInstanceKey =
        startProcessInstance(
                camundaClient, "multi_instance_subprocess", "{\"variables\": [\"foo\", \"bar\"]}")
            .getProcessInstanceKey();

    waitForProcessInstancesToStart(camundaClient, 1);
  }

  @AfterAll
  static void afterAll() {
    DEPLOYED_PROCESSES.clear();
    processInstanceKey = 0L;
  }

  @Test
  void shouldModifyProcessInstancesWithBatch() {
    // when
    final var result =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceModify()
            .moveInstruction("userTaskA", "userTaskB")
            .moveInstruction("userTaskC", "userTaskD")
            .filter(new ProcessInstanceFilterImpl())
            .send()
            .join();
    final var batchOperationKey = result.getBatchOperationKey();

    // then
    assertThat(result).isNotNull();
    //    assertThat(result.getBatchOperationKey()).isNotNull();

    // and
    await("should complete batch operation")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .pollInterval(Duration.ofMillis(100))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              // and
              final var batch =
                  camundaClient
                      .newBatchOperationGetRequest(result.getBatchOperationKey())
                      .send()
                      .join();
              assertThat(batch).isNotNull();
              assertThat(batch.getEndDate()).isNotNull();
              assertThat(batch.getStatus()).isEqualTo(BatchOperationState.COMPLETED);
              assertThat(batch.getOperationsCompletedCount()).isEqualTo(1);
              assertThat(batch.getOperationsFailedCount()).isEqualTo(0);
            });

    // and
    final var itemsObj =
        camundaClient.newBatchOperationItemsGetRequest(batchOperationKey).send().join();
    final var itemKeys = itemsObj.items().stream().map(BatchOperationItem::getKey).toList();

    assertThat(itemsObj.items()).hasSize(1);
    assertThat(itemsObj.items().stream().map(BatchOperationItem::getStatus).distinct().toList())
        .containsExactly(BatchOperationItemState.COMPLETED);
    assertThat(itemKeys).containsExactlyInAnyOrder(processInstanceKey);

    processInstanceHasActiveUserTasks(
        camundaClient,
        processInstanceKey,
        Map.of(
            "userTaskB", 1L,
            "userTaskD", 2L));
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
          assertThat(elementIdCounts).isEqualTo(userTaskCounts);
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
