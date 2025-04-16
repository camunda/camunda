/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.MigrationPlan;
import io.camunda.client.api.response.CreateBatchOperationResponse;
import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.filter.ProcessDefinitionFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.filter.VariableFilter;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import io.camunda.client.impl.search.filter.ProcessInstanceFilterImpl;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.assertj.core.api.ThrowingConsumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "es")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "os")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class BatchOperationMigrateProcessInstanceTest {

  private static CamundaClient client;

  @Test
  void shouldMigrateProcessInstancesWithBatch() {
    // given
    deployProcessFromClasspath(client, "process/migration-process_v1.bpmn");
    final var definitionKey2 =
        deployProcessFromClasspath(client, "process/migration-process_v2.bpmn");
    final var processInstanceKey =
        startProcessInstance(
            client,
            "migration-process_v1",
            Map.of(
                "foo", "bar",
                "alice", "bob"));

    // when
    final var batchCreated =
        batchMigrateProcessInstance(
            client,
            definitionKey2,
            MigrationPlan.newBuilder()
                .withTargetProcessDefinitionKey(definitionKey2)
                .addMappingInstruction("taskA", "taskA2")
                .addMappingInstruction("taskB", "taskB2")
                .addMappingInstruction("taskC", "taskC2")
                .build());

    // then
    Awaitility.await("should complete batch operation")
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(100))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              // and
              final var batch =
                  client
                      .newBatchOperationGetRequest(batchCreated.getBatchOperationKey())
                      .send()
                      .join();
              assertThat(batch).isNotNull();
              assertThat(batch.getEndDate()).isNotNull();
              assertThat(batch.getStatus()).isEqualTo(BatchOperationState.COMPLETED);
              assertThat(batch.getOperationsCompletedCount()).isEqualTo(1);
              assertThat(batch.getOperationsCompletedCount()).isEqualTo(1);
              assertThat(batch.getOperationsFailedCount()).isEqualTo(0);
            });

    Awaitility.await("should update batch operation items")
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(100))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              // and
              final var batchItems =
                  client
                      .newBatchOperationItemsGetRequest(batchCreated.getBatchOperationKey())
                      .send()
                      .join()
                      .items();
              assertThat(batchItems).isNotEmpty();
              assertThat(batchItems.getFirst().getStatus())
                  .isEqualTo(BatchOperationItemState.COMPLETED);
            });

    // and
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
        client,
        processInstanceKey,
        Map.of(
            "foo", v -> v.getValue().equals("\"bar\""),
            "alice", v -> v.getValue().equals("\"bob\"")));
  }

  public CreateBatchOperationResponse batchMigrateProcessInstance(
      final CamundaClient client,
      final Long targetProcessDefinitionKey,
      final MigrationPlan migrationPlan) {
    return client
        .newCreateBatchOperationCommand()
        .migrateProcessInstance()
        .targetProcessDefinitionKey(targetProcessDefinitionKey)
        .migrationPlan(migrationPlan)
        .filter(new ProcessInstanceFilterImpl())
        .send()
        .join();
  }

  public Long deployProcessFromClasspath(final CamundaClient client, final String classpath) {
    final var deployment =
        client.newDeployResourceCommand().addResourceFromClasspath(classpath).send().join();
    final var event = deployment.getProcesses().getFirst();

    // sync with exported database
    processDefinitionExistAndMatches(
        client,
        f -> f.processDefinitionKey(event.getProcessDefinitionKey()),
        f -> assertThat(f).hasSize(1));

    return event.getProcessDefinitionKey();
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
          assertThat(list).hasSize(assertions.size());
          list.forEach(v -> assertThat(assertions.get(v.getName()).test(v)).isTrue());
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

  public void processDefinitionExistAndMatches(
      final CamundaClient client,
      final Consumer<ProcessDefinitionFilter> filter,
      final Consumer<List<ProcessDefinition>> asserter) {
    await()
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var result =
                  client.newProcessDefinitionSearchRequest().filter(filter).send().join().items();
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

  public static Long startProcessInstance(
      final CamundaClient client, final String processId, final Map<String, Object> variables) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(variables)
        .send()
        .join()
        .getProcessInstanceKey();
  }
}
