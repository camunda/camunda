/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.command.MigrationPlan;
import io.camunda.client.api.search.response.FlowNodeInstance;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import io.camunda.it.utils.BrokerITInvocationProvider;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ProcessMigrationIT {

  @RegisterExtension
  static final BrokerITInvocationProvider PROVIDER = new BrokerITInvocationProvider();

  @TestTemplate
  void shouldMigrateProcess(final TestStandaloneBroker testBroker) {
    final var steps = new MigrationSteps(testBroker);

    // given
    steps
        .deployProcessFromClasspath("process/migration-process_v1.bpmn")
        .deployProcessFromClasspath("process/migration-process_v2.bpmn")
        .startProcessInstance(
            "migration-process_v1",
            Map.of(
                "foo", "bar",
                "alice", "bob"));

    // when
    steps.migrateProcessInstance(
        MigrationPlan.newBuilder()
            .withTargetProcessDefinitionKey(
                steps.getProcessDefinition("migration-process_v2").getProcessDefinitionKey())
            .addMappingInstruction("taskA", "taskA2")
            .addMappingInstruction("taskB", "taskB2")
            .addMappingInstruction("taskC", "taskC2")
            .build());

    // then
    steps
        .processInstanceExistAndMatch(
            f ->
                f.processInstanceKey(steps.processInstanceKey)
                    .processDefinitionId("migration-process_v2"),
            f -> assertThat(f).hasSize(1))
        .processInstanceHasUserTask(
            userTask -> {
              assertThat(userTask.getElementId()).isEqualTo("taskA2");
              assertThat(userTask.getBpmnProcessId()).isEqualTo("migration-process_v2");
              assertThat(userTask.getProcessDefinitionVersion()).isEqualTo(1);
            })
        .processInstanceHasFlowNodes(
            Map.of(
                "start", fni -> fni.getProcessDefinitionId().equals("migration-process_v1"),
                "gateway1", fni -> fni.getProcessDefinitionId().equals("migration-process_v1"),
                "taskA2", fni -> fni.getProcessDefinitionId().equals("migration-process_v2"),
                "taskB2", fni -> fni.getProcessDefinitionId().equals("migration-process_v2")))
        .processInstanceHasVariables(
            Map.of(
                "foo", v -> v.getValue().equals("\"bar\""),
                "alice", v -> v.getValue().equals("\"bob\"")));
  }

  @TestTemplate
  void shouldMigrateProcessWithIncident(final TestStandaloneBroker testBroker) {
    final var steps = new MigrationSteps(testBroker);

    // given
    steps
        .deployProcessFromClasspath("process/migration-process_v1.bpmn")
        .deployProcessFromClasspath("process/migration-process_v2.bpmn")
        .startProcessInstance("migration-process_v1")
        .throwIncident("taskB", "error", "error message");

    // when
    steps.migrateProcessInstance(
        MigrationPlan.newBuilder()
            .withTargetProcessDefinitionKey(
                steps.getProcessDefinition("migration-process_v2").getProcessDefinitionKey())
            .addMappingInstruction("taskA", "taskA2")
            .addMappingInstruction("taskB", "taskB2")
            .addMappingInstruction("taskC", "taskC2")
            .build());

    // then
    steps
        .flowNodeHasIncident("taskB2")
        .processInstanceHasIncident(
            i -> {
              assertThat(i.getFlowNodeId()).isEqualTo("taskB2");
              assertThat(i.getProcessDefinitionId()).isEqualTo("migration-process_v2");
              assertThat(i.getState()).isEqualTo("ACTIVE");
            });
  }

  @TestTemplate
  void shouldMigrateProcessWithResolvedIncident(final TestStandaloneBroker testBroker) {
    final var steps = new MigrationSteps(testBroker);

    // given
    steps
        .deployProcessFromClasspath("process/migration-process_v1.bpmn")
        .deployProcessFromClasspath("process/migration-process_v2.bpmn")
        .startProcessInstance("migration-process_v1")
        .throwIncident("taskB", "error", "error message")
        .resolveIncidents();

    // when
    steps.migrateProcessInstance(
        MigrationPlan.newBuilder()
            .withTargetProcessDefinitionKey(
                steps.getProcessDefinition("migration-process_v2").getProcessDefinitionKey())
            .addMappingInstruction("taskA", "taskA2")
            .addMappingInstruction("taskB", "taskB2")
            .addMappingInstruction("taskC", "taskC2")
            .build());

    // then
    steps.processInstanceHasIncident(
        i -> {
          assertThat(i.getFlowNodeId()).isEqualTo("taskB");
          assertThat(i.getProcessDefinitionId()).isEqualTo("migration-process_v1");
          assertThat(i.getState()).isEqualTo("RESOLVED");
        });
  }

  static class MigrationSteps extends TestSteps<MigrationSteps> {

    protected long incidentKey;

    public MigrationSteps(final TestStandaloneBroker broker) {
      super(broker);
    }

    // ----------------------
    // GIVEN / WHEN
    // ----------------------

    public MigrationSteps migrateProcessInstance(final MigrationPlan migrationPlan) {
      client
          .newMigrateProcessInstanceCommand(processInstanceKey)
          .migrationPlan(migrationPlan)
          .send()
          .join();

      return self();
    }

    public MigrationSteps throwIncident(
        final String jobType, final String errorCode, final String errorMessage) {
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
          f -> f.processInstanceKey(processInstanceKey), l -> assertThat(l).hasSize(1));

      return self();
    }

    public MigrationSteps resolveIncidents() {
      client
          .newIncidentQuery()
          .filter(f -> f.processInstanceKey(processInstanceKey))
          .send()
          .join()
          .items()
          .forEach(i -> client.newResolveIncidentCommand(i.getIncidentKey()).send().join());

      return self();
    }

    // ----------------------
    // THEN
    // ----------------------

    public MigrationSteps processInstanceHasFlowNodes(
        final Map<String, Predicate<FlowNodeInstance>> flowNodeAssertions) {
      return flowNodeInstanceExistAndMatch(
          f -> f.processInstanceKey(processInstanceKey),
          f -> {
            assertThat(f).hasSize(flowNodeAssertions.size());
            f.forEach(
                fni -> assertThat(flowNodeAssertions.get(fni.getFlowNodeId()).test(fni)).isTrue());
          });
    }

    public MigrationSteps processInstanceHasVariables(
        final Map<String, Predicate<Variable>> assertions) {
      return variableExistAndMatch(
          f -> f.processInstanceKey(processInstanceKey),
          list -> {
            assertThat(list).hasSize(assertions.size());
            list.forEach(v -> assertThat(assertions.get(v.getName()).test(v)).isTrue());
          });
    }

    public MigrationSteps flowNodeHasIncident(final String flowNodeId) {
      return flowNodeInstanceExistAndMatch(
          f -> f.processInstanceKey(processInstanceKey).flowNodeId(flowNodeId),
          f -> {
            assertThat(f).hasSize(1);
            assertThat(f.getFirst().getIncident()).isTrue();
          });
    }

    public MigrationSteps processInstanceHasIncident(final ThrowingConsumer<Incident> assertions) {
      return incidentsExistAndMatch(
          f -> f.processInstanceKey(processInstanceKey),
          f -> {
            assertThat(f).hasSize(1);
            assertThat(f.getFirst()).satisfies(assertions);
          });
    }

    public MigrationSteps processInstanceHasUserTask(final ThrowingConsumer<UserTask> assertions) {
      return userTaskExistAndMatch(
          f -> f.processInstanceKey(processInstanceKey),
          f -> {
            assertThat(f).hasSize(1);
            assertThat(f.getFirst()).satisfies(assertions);
          });
    }
  }
}
