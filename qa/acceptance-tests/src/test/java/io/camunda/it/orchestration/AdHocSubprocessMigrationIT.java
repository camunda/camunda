/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.orchestration;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static io.camunda.zeebe.model.bpmn.impl.ZeebeConstants.AD_HOC_SUB_PROCESS_ELEMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.MigrationPlan;
import io.camunda.client.api.search.filter.ProcessDefinitionFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.filter.VariableFilter;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Acceptance tests for process instance migration with Ad Hoc Subprocess functionality. These tests
 * verify end-to-end migration scenarios involving ad hoc subprocesses.
 */
@MultiDbTest
@Execution(ExecutionMode.SAME_THREAD)
public class AdHocSubprocessMigrationIT {

  private static CamundaClient client;

  @Test
  void shouldMigrateProcessWithActiveAdHocSubprocess() {
    // given
    deployProcessFromClasspath(client, "process/migration-ahsp-process_v1.bpmn");

    final var targetProcess =
        deployProcessFromClasspath(client, "process/migration-ahsp-process_v2.bpmn");

    final var processInstanceKey =
        startProcessInstance(
            client,
            "migration-ahsp-process_v1",
            Map.of(
                "foo", "bar",
                "alice", "bob"));
    processInstanceHasVariables(
        client,
        processInstanceKey,
        Map.of(
            AD_HOC_SUB_PROCESS_ELEMENTS,
            v -> v.getValue().equals("[{\"elementId\":\"A\",\"elementName\":\"A\"}]"),
            "foo",
            v -> v.getValue().equals("\"bar\""),
            "alice",
            v -> v.getValue().equals("\"bob\"")));

    // Ensure user task A is active before migration
    processInstanceHasUserTask(
        client,
        processInstanceKey,
        1,
        userTask -> {
          assertThat(userTask.getElementId()).isEqualTo("A");
          assertThat(userTask.getBpmnProcessId()).isEqualTo("migration-ahsp-process_v1");
        });

    // when
    migrateProcessInstance(
        client,
        processInstanceKey,
        MigrationPlan.newBuilder()
            .withTargetProcessDefinitionKey(targetProcess)
            .addMappingInstruction("AD_HOC_SUBPROCESS", "AD_HOC_SUBPROCESS")
            .addMappingInstruction("A", "D")
            .build());

    // then
    processInstanceExistAndMatches(
        client,
        f ->
            f.processInstanceKey(processInstanceKey)
                .processDefinitionId("migration-ahsp-process_v2"),
        f -> assertThat(f).hasSize(1));
    processInstanceHasUserTask(
        client,
        processInstanceKey,
        1,
        userTask -> {
          assertThat(userTask.getElementId()).isEqualTo("D");
          assertThat(userTask.getBpmnProcessId()).isEqualTo("migration-ahsp-process_v2");
          assertThat(userTask.getProcessDefinitionVersion()).isEqualTo(1);
        });
    processInstanceHasVariables(
        client,
        processInstanceKey,
        Map.of(
            AD_HOC_SUB_PROCESS_ELEMENTS,
            v -> v.getValue().equals("[{\"elementId\":\"D\",\"elementName\":\"D\"}]"),
            "foo",
            v -> v.getValue().equals("\"bar\""),
            "alice",
            v -> v.getValue().equals("\"bob\"")));
  }

  @Test
  void shouldMigrateProcessWithActiveMultiInstanceParallelAdHocSubprocess() {
    // given
    deployProcessFromClasspath(client, "process/migration-mip-ahsp_v1.bpmn");

    final var targetProcess =
        deployProcessFromClasspath(client, "process/migration-mip-ahsp_v2.bpmn");

    final var processInstanceKey =
        startProcessInstance(
            client,
            "migration-mip-ahsp_v1",
            Map.of(
                "foo", "bar",
                "alice", "bob"));
    processInstanceHasVariables(
        client,
        processInstanceKey,
        Map.of(
            AD_HOC_SUB_PROCESS_ELEMENTS,
            v -> v.getValue().equals("[{\"elementId\":\"A\",\"elementName\":\"A\"}]"),
            "foo",
            v -> v.getValue().equals("\"bar\""),
            "alice",
            v -> v.getValue().equals("\"bob\"")));

    // Ensure user tasks A are active before migration (2 tasks due to parallel MI with 2
    // iterations)
    processInstanceHasUserTask(
        client,
        processInstanceKey,
        2,
        userTask -> {
          assertThat(userTask.getElementId()).isEqualTo("A");
          assertThat(userTask.getBpmnProcessId()).isEqualTo("migration-mip-ahsp_v1");
        });

    // when
    migrateProcessInstance(
        client,
        processInstanceKey,
        MigrationPlan.newBuilder()
            .withTargetProcessDefinitionKey(targetProcess)
            .addMappingInstruction("AD_HOC_MULTI_PARALLEL_1", "AD_HOC_MULTI_PARALLEL_2")
            .addMappingInstruction("A", "D")
            .build());

    // then
    processInstanceExistAndMatches(
        client,
        f -> f.processInstanceKey(processInstanceKey).processDefinitionId("migration-mip-ahsp_v2"),
        f -> assertThat(f).hasSize(1));
    processInstanceHasUserTask(
        client,
        processInstanceKey,
        2,
        userTask -> {
          assertThat(userTask.getElementId()).isEqualTo("D");
          assertThat(userTask.getBpmnProcessId()).isEqualTo("migration-mip-ahsp_v2");
          assertThat(userTask.getProcessDefinitionVersion()).isEqualTo(1);
        });
    processInstanceHasVariables(
        client,
        processInstanceKey,
        Map.of(
            AD_HOC_SUB_PROCESS_ELEMENTS,
            v -> v.getValue().equals("[{\"elementId\":\"D\",\"elementName\":\"D\"}]"),
            "foo",
            v -> v.getValue().equals("\"bar\""),
            "alice",
            v -> v.getValue().equals("\"bob\"")));
  }

  @Test
  void shouldMigrateProcessWithActiveMultiInstanceSequentialAdHocSubprocess() {
    // given
    deployProcessFromClasspath(client, "process/migration-mis-ahsp_v1.bpmn");

    final var targetProcess =
        deployProcessFromClasspath(client, "process/migration-mis-ahsp_v2.bpmn");

    final var processInstanceKey =
        startProcessInstance(
            client,
            "migration-mis-ahsp_v1",
            Map.of(
                "foo", "bar",
                "alice", "bob"));
    processInstanceHasVariables(
        client,
        processInstanceKey,
        Map.of(
            AD_HOC_SUB_PROCESS_ELEMENTS,
            v -> v.getValue().equals("[{\"elementId\":\"A\",\"elementName\":\"A\"}]"),
            "foo",
            v -> v.getValue().equals("\"bar\""),
            "alice",
            v -> v.getValue().equals("\"bob\"")));

    // Ensure user task A is active before migration (only 1 task due to sequential MI)
    processInstanceHasUserTask(
        client,
        processInstanceKey,
        1,
        userTask -> {
          assertThat(userTask.getElementId()).isEqualTo("A");
          assertThat(userTask.getBpmnProcessId()).isEqualTo("migration-mis-ahsp_v1");
        });

    // when
    migrateProcessInstance(
        client,
        processInstanceKey,
        MigrationPlan.newBuilder()
            .withTargetProcessDefinitionKey(targetProcess)
            .addMappingInstruction("AD_HOC_MULTI_SEQUENTIAL_1", "AD_HOC_MULTI_SEQUENTIAL_2")
            .addMappingInstruction("A", "D")
            .build());

    // then
    processInstanceExistAndMatches(
        client,
        f -> f.processInstanceKey(processInstanceKey).processDefinitionId("migration-mis-ahsp_v2"),
        f -> assertThat(f).hasSize(1));
    processInstanceHasUserTask(
        client,
        processInstanceKey,
        1,
        userTask -> {
          assertThat(userTask.getElementId()).isEqualTo("D");
          assertThat(userTask.getBpmnProcessId()).isEqualTo("migration-mis-ahsp_v2");
          assertThat(userTask.getProcessDefinitionVersion()).isEqualTo(1);
        });
    processInstanceHasVariables(
        client,
        processInstanceKey,
        Map.of(
            AD_HOC_SUB_PROCESS_ELEMENTS,
            v -> v.getValue().equals("[{\"elementId\":\"D\",\"elementName\":\"D\"}]"),
            "foo",
            v -> v.getValue().equals("\"bar\""),
            "alice",
            v -> v.getValue().equals("\"bob\"")));
  }

  //
  // Helper methods
  //

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

  private void migrateProcessInstance(
      final CamundaClient client,
      final Long processInstanceKey,
      final MigrationPlan migrationPlan) {
    client
        .newMigrateProcessInstanceCommand(processInstanceKey)
        .migrationPlan(migrationPlan)
        .send()
        .join();
  }

  private void processDefinitionExistAndMatches(
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

  private void processInstanceExistAndMatches(
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

  private void userTaskExistAndMatches(
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

  private void processInstanceHasUserTask(
      final CamundaClient client,
      final Long processInstanceKey,
      final int numberOfUserTasks,
      final ThrowingConsumer<UserTask> assertions) {
    userTaskExistAndMatches(
        client,
        f -> f.processInstanceKey(processInstanceKey),
        f -> {
          assertThat(f).hasSize(numberOfUserTasks);
          assertThat(f.getFirst()).satisfies(assertions);
        });
  }

  private void processInstanceHasVariables(
      final CamundaClient client,
      final Long processInstanceKey,
      final Map<String, Predicate<Variable>> assertions) {
    variableExistAndMatches(
        client,
        f -> f.processInstanceKey(processInstanceKey),
        list -> {
          assertThat(list).hasSizeGreaterThanOrEqualTo(assertions.size());
          list.forEach(
              v -> {
                if (assertions.containsKey(v.getName())) {
                  assertThat(assertions.get(v.getName()).test(v)).isTrue();
                }
              });
        });
  }

  private void variableExistAndMatches(
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

  private static Long startProcessInstance(
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
