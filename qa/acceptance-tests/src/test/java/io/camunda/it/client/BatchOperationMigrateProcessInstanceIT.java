/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.VAR_TEST_SCOPE_ID;
import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.getScopedVariables;
import static io.camunda.it.util.TestHelper.startScopedProcessInstance;
import static io.camunda.it.util.TestHelper.waitForActiveScopedUserTasks;
import static io.camunda.it.util.TestHelper.waitForBatchOperationCompleted;
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static io.camunda.it.util.TestHelper.waitForScopedProcessInstancesToStart;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.MigrationPlan;
import io.camunda.client.api.response.CreateBatchOperationResponse;
import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.filter.VariableFilter;
import io.camunda.client.api.search.response.BatchOperationItems.BatchOperationItem;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import io.camunda.client.impl.search.filter.ProcessInstanceFilterImpl;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.assertj.core.api.ThrowingConsumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@CompatibilityTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class BatchOperationMigrateProcessInstanceIT {

  private static CamundaClient client;

  @Test
  void shouldMigrateProcessInstancesWithBatch(final TestInfo testInfo) {
    // given
    final String testScopeId =
        testInfo.getTestMethod().map(Method::toString).orElse(UUID.randomUUID().toString());

    final long sourceProcessDefinitionKey =
        deployProcessAndWaitForIt(client, "process/migration-process_v1.bpmn")
            .getProcessDefinitionKey();
    final var targetProcessDefinitionKey =
        deployProcessAndWaitForIt(client, "process/migration-process_v2.bpmn")
            .getProcessDefinitionKey();

    final List<Long> processInstances = new ArrayList<>();
    IntStream.range(0, 10)
        .forEach(
            i ->
                processInstances.add(
                    startScopedProcessInstance(
                            client, sourceProcessDefinitionKey, testScopeId, Map.of("foo", "bar"))
                        .getProcessInstanceKey()));

    waitForScopedProcessInstancesToStart(client, testScopeId, processInstances.size());
    waitForActiveScopedUserTasks(client, testScopeId, processInstances.size());

    // when
    final var batchCreated =
        batchMigrateProcessInstance(
            client,
            testScopeId,
            sourceProcessDefinitionKey,
            MigrationPlan.newBuilder()
                .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
                .addMappingInstruction("taskA", "taskA2")
                .addMappingInstruction("taskB", "taskB2")
                .addMappingInstruction("taskC", "taskC2")
                .build());

    // then wait if batch has correct amount of items. (To fail fast if not)
    waitForBatchOperationWithCorrectTotalCount(
        client, batchCreated.getBatchOperationKey(), processInstances.size());

    // and wait for the batch operation to complete
    waitForBatchOperationCompleted(
        client, batchCreated.getBatchOperationKey(), processInstances.size(), 0);

    Awaitility.await("should update batch operation items")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              // and
              final var batchItems =
                  client
                      .newBatchOperationItemsSearchRequest()
                      .filter(f -> f.batchOperationKey(batchCreated.getBatchOperationKey()))
                      .send()
                      .join()
                      .items();
              assertThat(batchItems).isNotEmpty();
              assertThat(batchItems).hasSize(processInstances.size());
              assertThat(batchItems.stream().map(BatchOperationItem::getStatus).distinct().toList())
                  .containsExactly(BatchOperationItemState.COMPLETED);
            });

    // and
    for (final var processInstanceKey : processInstances) {
      processInstanceExistAndMatches(
          client,
          f -> f.processInstanceKey(processInstanceKey).processDefinitionId("migration-process_v2"),
          f -> assertThat(f).hasSize(1));
      processInstanceHasUserTask(
          client,
          processInstanceKey,
          userTask -> {
            assertThat(userTask.getElementId()).isEqualTo("taskA2");
            assertThat(userTask.getBpmnProcessId()).isEqualTo("migration-process_v2");
            assertThat(userTask.getProcessDefinitionVersion()).isEqualTo(1);
          });
      processInstanceHasElementInstances(
          client,
          processInstanceKey,
          Map.of(
              "start", fni -> fni.getProcessDefinitionId().equals("migration-process_v1"),
              "gateway1", fni -> fni.getProcessDefinitionId().equals("migration-process_v1"),
              "taskA2", fni -> fni.getProcessDefinitionId().equals("migration-process_v2"),
              "taskB2", fni -> fni.getProcessDefinitionId().equals("migration-process_v2")));
      processInstanceHasVariables(
          client, processInstanceKey, Map.of("foo", v -> v.getValue().equals("\"bar\"")));
    }
  }

  @Test
  void shouldMigrateProcessInstancesWithFailuresWithBatch(final TestInfo testInfo) {
    // given
    final String testScopeId =
        testInfo.getTestMethod().map(Method::toString).orElse(UUID.randomUUID().toString());

    final long sourceProcessDefinitionKey =
        deployProcessAndWaitForIt(
                client,
                Bpmn.createExecutableProcess("sourceProcess")
                    .startEvent()
                    .exclusiveGateway()
                    .conditionExpression("canBeMigrated")
                    .userTask("userTaskA")
                    .moveToLastExclusiveGateway()
                    .defaultFlow()
                    .userTask("willNotBeMigrated")
                    .done(),
                "source-process.bpmn")
            .getProcessDefinitionKey();

    final var targetProcessDefinitionKey =
        deployProcessAndWaitForIt(
                client,
                Bpmn.createExecutableProcess("process2").startEvent().userTask("userTaskB").done(),
                "target-process.bpmn")
            .getProcessDefinitionKey();

    // start 10 process instances, 5 of them can be migrated and 5 cannot
    final List<Long> processInstances = new ArrayList<>();
    IntStream.rangeClosed(0, 9)
        .forEach(
            i ->
                processInstances.add(
                    startScopedProcessInstance(
                            client,
                            sourceProcessDefinitionKey,
                            testScopeId,
                            Map.of("canBeMigrated", i % 2 == 0))
                        .getProcessInstanceKey()));

    waitForScopedProcessInstancesToStart(client, testScopeId, processInstances.size());
    // when
    final var batchCreated =
        batchMigrateProcessInstance(
            client,
            testScopeId,
            sourceProcessDefinitionKey,
            MigrationPlan.newBuilder()
                .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
                .addMappingInstruction("userTaskA", "userTaskB")
                .build());

    // and wait for the batch operation to complete
    waitForBatchOperationCompleted(client, batchCreated.getBatchOperationKey(), 5, 5);
  }

  public CreateBatchOperationResponse batchMigrateProcessInstance(
      final CamundaClient client,
      final String scopeId,
      final long sourceProcessDefinitionKey,
      final MigrationPlan migrationPlan) {
    return client
        .newCreateBatchOperationCommand()
        .migrateProcessInstance()
        .migrationPlan(migrationPlan)
        .filter(
            new ProcessInstanceFilterImpl()
                .processDefinitionKey(sourceProcessDefinitionKey)
                .variables(getScopedVariables(scopeId)))
        .send()
        .join();
  }

  //
  // Query helpers
  //
  public void processInstanceHasUserTask(
      final CamundaClient client,
      final Long processInstanceKey,
      final ThrowingConsumer<UserTask> assertions) {
    userTaskExistAndMatches(
        client,
        f -> f.processInstanceKey(processInstanceKey),
        f -> {
          assertThat(f).hasSize(1);
          assertThat(f.getFirst()).satisfies(assertions);
        });
  }

  public void processInstanceHasElementInstances(
      final CamundaClient client,
      final Long processInstanceKey,
      final Map<String, Predicate<ElementInstance>> flowNodeAssertions) {
    flowNodeInstanceExistAndMatches(
        client,
        f -> f.processInstanceKey(processInstanceKey),
        f -> {
          assertThat(f).hasSize(flowNodeAssertions.size());
          f.forEach(
              fni -> assertThat(flowNodeAssertions.get(fni.getElementId()).test(fni)).isTrue());
        });
  }

  public void processInstanceHasVariables(
      final CamundaClient client,
      final Long processInstanceKey,
      final Map<String, Predicate<Variable>> assertions) {
    variableExistAndMatches(
        client,
        f -> f.processInstanceKey(processInstanceKey),
        list -> {
          assertThat(list).hasSize(assertions.size() + 1); // +1 for scope variable
          list.stream()
              .filter(v -> !v.getName().equals(VAR_TEST_SCOPE_ID))
              .forEach(v -> assertThat(assertions.get(v.getName()).test(v)).isTrue());
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

  public void variableExistAndMatches(
      final CamundaClient client,
      final Consumer<VariableFilter> filter,
      final Consumer<List<Variable>> asserter) {
    await()
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var result =
                  client.newVariableSearchRequest().filter(filter).send().join().items();
              asserter.accept(result);
            });
  }

  public void processInstanceExistAndMatches(
      final CamundaClient client,
      final Consumer<ProcessInstanceFilter> filter,
      final Consumer<List<ProcessInstance>> asserter) {
    await()
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var result =
                  client.newProcessInstanceSearchRequest().filter(filter).send().join().items();
              asserter.accept(result);
            });
  }

  public void userTaskExistAndMatches(
      final CamundaClient client,
      final Consumer<UserTaskFilter> filter,
      final Consumer<List<UserTask>> asserter) {
    await()
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var result =
                  client.newUserTaskSearchRequest().filter(filter).send().join().items();
              asserter.accept(result);
            });
  }
}
