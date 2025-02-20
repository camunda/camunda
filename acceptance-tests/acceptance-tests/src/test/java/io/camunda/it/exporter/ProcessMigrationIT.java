/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.exporter;

import static io.camunda.it.exporter.ExporterTestUtil.startProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.MigrationPlan;
import io.camunda.client.api.search.filter.FlownodeInstanceFilter;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.filter.ProcessDefinitionFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.filter.VariableFilter;
import io.camunda.client.api.search.response.FlowNodeInstance;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.IncidentState;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import io.camunda.it.utils.BrokerITInvocationProvider;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(BrokerITInvocationProvider.class)
public class ProcessMigrationIT {

  @TestTemplate
  void shouldMigrateProcess(final TestStandaloneBroker testBroker) {
    final var client = testBroker.newClientBuilder().build();

    // given
    final var definitionKey1 =
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
    migrateProcessInstance(
        client,
        processInstanceKey,
        MigrationPlan.newBuilder()
            .withTargetProcessDefinitionKey(definitionKey2)
            .addMappingInstruction("taskA", "taskA2")
            .addMappingInstruction("taskB", "taskB2")
            .addMappingInstruction("taskC", "taskC2")
            .build());

    // then
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
    processInstanceHasFlowNodes(
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

  @TestTemplate
  void shouldMigrateProcessWithIncident(final TestStandaloneBroker testBroker) {
    final var client = testBroker.newClientBuilder().build();

    // given
    final var definitionKey1 =
        deployProcessFromClasspath(client, "process/migration-process_v1.bpmn");
    final var definitionKey2 =
        deployProcessFromClasspath(client, "process/migration-process_v2.bpmn");
    final var processInstanceKey = startProcessInstance(client, "migration-process_v1");
    throwIncident(client, processInstanceKey, "taskB", "error", "error message");

    // when
    migrateProcessInstance(
        client,
        processInstanceKey,
        MigrationPlan.newBuilder()
            .withTargetProcessDefinitionKey(definitionKey2)
            .addMappingInstruction("taskA", "taskA2")
            .addMappingInstruction("taskB", "taskB2")
            .addMappingInstruction("taskC", "taskC2")
            .build());

    // then
    flowNodeHasIncident(client, processInstanceKey, "taskB2");
    processInstanceHasIncident(
        client,
        processInstanceKey,
        i -> {
          assertThat(i.getFlowNodeId()).isEqualTo("taskB2");
          assertThat(i.getProcessDefinitionId()).isEqualTo("migration-process_v2");
          assertThat(i.getState()).isEqualTo(IncidentState.ACTIVE);
        });
  }

  @TestTemplate
  void shouldMigrateProcessWithResolvedIncident(final TestStandaloneBroker testBroker) {
    final var client = testBroker.newClientBuilder().build();

    // given
    final var definitionKey1 =
        deployProcessFromClasspath(client, "process/migration-process_v1.bpmn");
    final var definitionKey2 =
        deployProcessFromClasspath(client, "process/migration-process_v2.bpmn");
    final var processInstanceKey = startProcessInstance(client, "migration-process_v1");
    throwIncident(client, processInstanceKey, "taskB", "error", "error message");
    resolveIncidents(client, processInstanceKey);

    // when
    migrateProcessInstance(
        client,
        processInstanceKey,
        MigrationPlan.newBuilder()
            .withTargetProcessDefinitionKey(definitionKey2)
            .addMappingInstruction("taskA", "taskA2")
            .addMappingInstruction("taskB", "taskB2")
            .addMappingInstruction("taskC", "taskC2")
            .build());

    // then
    processInstanceHasIncident(
        client,
        processInstanceKey,
        i -> {
          assertThat(i.getFlowNodeId()).isEqualTo("taskB");
          assertThat(i.getProcessDefinitionId()).isEqualTo("migration-process_v1");
          assertThat(i.getState()).isEqualTo(IncidentState.RESOLVED);
        });
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
  // command helpers
  //

  public void migrateProcessInstance(
      final CamundaClient client,
      final Long processInstanceKey,
      final MigrationPlan migrationPlan) {
    client
        .newMigrateProcessInstanceCommand(processInstanceKey)
        .migrationPlan(migrationPlan)
        .send()
        .join();
  }

  public void throwIncident(
      final CamundaClient client,
      final Long processInstanceKey,
      final String jobType,
      final String errorCode,
      final String errorMessage) {
    client
        .newActivateJobsCommand()
        .jobType(jobType)
        .maxJobsToActivate(10)
        .workerName(UUID.randomUUID().toString())
        .send()
        .join()
        .getJobs()
        .forEach(
            j ->
                client
                    .newThrowErrorCommand(j.getKey())
                    .errorCode(errorCode)
                    .errorMessage(errorMessage)
                    .send()
                    .join());

    // wait for the incident to be in the database
    incidentsExistAndMatch(
        client, f -> f.processInstanceKey(processInstanceKey), l -> assertThat(l).hasSize(1));
  }

  public void resolveIncidents(final CamundaClient client, final Long processInstanceKey) {
    client
        .newIncidentQuery()
        .filter(f -> f.processInstanceKey(processInstanceKey))
        .send()
        .join()
        .items()
        .forEach(i -> client.newResolveIncidentCommand(i.getIncidentKey()).send().join());
  }

  //
  // Query helpers
  //

  public void incidentsExistAndMatch(
      final CamundaClient client,
      final Consumer<IncidentFilter> filter,
      final Consumer<List<Incident>> asserter) {
    await()
        .untilAsserted(
            () -> {
              final var incidents = client.newIncidentQuery().filter(filter).send().join().items();
              asserter.accept(incidents);
            });
  }

  public void flowNodeHasIncident(
      final CamundaClient client, final Long processInstanceKey, final String flowNodeId) {
    flowNodeInstanceExistAndMatches(
        client,
        f -> f.processInstanceKey(processInstanceKey).flowNodeId(flowNodeId),
        f -> {
          assertThat(f).hasSize(1);
          assertThat(f.getFirst().getIncident()).isTrue();
        });
  }

  public void processInstanceHasIncident(
      final CamundaClient client,
      final Long processInstanceKey,
      final ThrowingConsumer<Incident> assertions) {
    incidentsExistAndMatch(
        client,
        f -> f.processInstanceKey(processInstanceKey),
        f -> {
          assertThat(f).hasSize(1);
          assertThat(f.getFirst()).satisfies(assertions);
        });
  }

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

  public void processInstanceHasFlowNodes(
      final CamundaClient client,
      final Long processInstanceKey,
      final Map<String, Predicate<FlowNodeInstance>> flowNodeAssertions) {
    flowNodeInstanceExistAndMatches(
        client,
        f -> f.processInstanceKey(processInstanceKey),
        f -> {
          assertThat(f).hasSize(flowNodeAssertions.size());
          f.forEach(
              fni -> assertThat(flowNodeAssertions.get(fni.getFlowNodeId()).test(fni)).isTrue());
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
      final Consumer<FlownodeInstanceFilter> filter,
      final Consumer<List<FlowNodeInstance>> asserter) {
    await()
        .untilAsserted(
            () -> {
              final var result =
                  client.newFlownodeInstanceQuery().filter(filter).send().join().items();
              asserter.accept(result);
            });
  }

  public void variableExistAndMatches(
      final CamundaClient client,
      final Consumer<VariableFilter> filter,
      final Consumer<List<Variable>> asserter) {
    await()
        .untilAsserted(
            () -> {
              final var result = client.newVariableQuery().filter(filter).send().join().items();
              asserter.accept(result);
            });
  }

  public void processDefinitionExistAndMatches(
      final CamundaClient client,
      final Consumer<ProcessDefinitionFilter> filter,
      final Consumer<List<ProcessDefinition>> asserter) {
    await()
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var result =
                  client.newProcessDefinitionQuery().filter(filter).send().join().items();
              asserter.accept(result);
            });
  }

  public void processInstanceExistAndMatches(
      final CamundaClient client,
      final Consumer<ProcessInstanceFilter> filter,
      final Consumer<List<ProcessInstance>> asserter) {
    await()
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var result =
                  client.newProcessInstanceQuery().filter(filter).send().join().items();
              asserter.accept(result);
            });
  }

  public void userTaskExistAndMatches(
      final CamundaClient client,
      final Consumer<UserTaskFilter> filter,
      final Consumer<List<UserTask>> asserter) {
    await()
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var result = client.newUserTaskQuery().filter(filter).send().join().items();
              asserter.accept(result);
            });
  }
}
