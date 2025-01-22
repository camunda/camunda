/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.response.SearchQueryResponse;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.it.utils.CamundaMultiDBExtension;
import io.camunda.it.utils.MultiDbTest;
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * TODO: We currently only test CamundaExporter (Elasticsearch) because purge is not implemented in
 * the others (yet).
 */
@MultiDbTest
public class ClusterPurgeMultiDbIT {

  private static CamundaClient client;

  private static final TestStandaloneApplication<?> APPLICATION =
      new TestStandaloneBroker().withUnauthenticatedAccess();

  @RegisterExtension
  public static final CamundaMultiDBExtension EXTENSION = new CamundaMultiDBExtension(APPLICATION);

  private static final int TIMEOUT = 40;

  @Test
  void shouldPurgeProcessDefinitions() {
    // GIVEN
    final ClusterActuator actuator = ClusterActuator.of(APPLICATION);
    final var processModel =
        Bpmn.createExecutableProcess("test-process").startEvent().endEvent().done();
    final var processDefinitionKey = deployProcessModel(processModel);

    // WHEN
    final var planChangeResponse = actuator.purge(false);

    // THEN
    assertThatChangesAreApplied(planChangeResponse);
    assertThatEntityNotFound(
        () -> client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send());
    assertThatEntityNotFound(
        () -> client.newProcessDefinitionGetRequest(processDefinitionKey).send());
  }

  @Test
  void shouldPurgeProcessInstances() {
    // GIVEN
    final ClusterActuator actuator = ClusterActuator.of(APPLICATION);
    final var processModel =
        Bpmn.createExecutableProcess("test-process")
            .startEvent()
            .serviceTask("service-task-1")
            .zeebeJobType("test")
            .endEvent()
            .done();
    final var processDefinitionKey = deployProcessModel(processModel);
    final var processInstanceKey = startProcess(processDefinitionKey);

    // WHEN
    final var planChangeResponse = actuator.purge(false);

    // THEN
    assertThatChangesAreApplied(planChangeResponse);
    assertThatEntityNotFound(() -> client.newCancelInstanceCommand(processInstanceKey).send());
    assertThatEntityNotFound(() -> client.newProcessInstanceGetRequest(processInstanceKey).send());
  }

  @Test
  void shouldPurgeServiceTask() {
    // GIVEN
    final ClusterActuator actuator = ClusterActuator.of(APPLICATION);
    final var processModel =
        Bpmn.createExecutableProcess("test-process")
            .startEvent()
            .serviceTask("service-task-1")
            .zeebeJobType("test")
            .endEvent()
            .done();
    final var processDefinitionKey = deployProcessModel(processModel);
    final var processInstanceKey = startProcess(processDefinitionKey);

    final var activeJob =
        client
            .newActivateJobsCommand()
            .jobType("test")
            .maxJobsToActivate(1)
            .send()
            .join()
            .getJobs()
            .getFirst();

    // WHEN
    final var planChangeResponse = actuator.purge(false);

    // THEN
    assertThatChangesAreApplied(planChangeResponse);
    assertThatEntityNotFound(() -> client.newCompleteCommand(activeJob).send());
  }

  @Test
  void shouldPurgeUserTask() {
    // GIVEN
    final ClusterActuator actuator = ClusterActuator.of(APPLICATION);
    final var processModel =
        Bpmn.createExecutableProcess("test-process")
            .startEvent()
            .userTask("user-task-1", AbstractUserTaskBuilder::zeebeUserTask)
            .endEvent()
            .done();
    final var processDefinitionKey = deployProcessModel(processModel);
    final var processInstanceKey = startProcess(processDefinitionKey);

    final AtomicReference<Long> userTaskKey = new AtomicReference<>();
    Awaitility.await("until user task is active")
        .ignoreExceptions()
        .atMost(Duration.ofSeconds(2 * TIMEOUT))
        .untilAsserted(
            () -> {
              final Future<SearchQueryResponse<UserTask>> userTaskFuture =
                  client.newUserTaskQuery().send();
              Assertions.assertThat(userTaskFuture)
                  .succeedsWithin(Duration.ofSeconds(TIMEOUT))
                  .extracting(SearchQueryResponse::items)
                  .satisfies(
                      items -> {
                        Assertions.assertThat(items).hasSize(1);
                        userTaskKey.set(items.getFirst().getUserTaskKey());
                      });
            });

    // WHEN
    final var planChangeResponse = actuator.purge(false);

    // THEN
    assertThatChangesAreApplied(planChangeResponse);
    assertThatEntityNotFound(() -> client.newUserTaskCompleteCommand(userTaskKey.get()).send());

    Awaitility.await("until user task query returns empty list")
        .atMost(Duration.ofSeconds(2 * TIMEOUT))
        .untilAsserted(
            () -> {
              final Future<SearchQueryResponse<UserTask>> userTaskFuture =
                  client.newUserTaskQuery().send();
              Assertions.assertThat(userTaskFuture)
                  .succeedsWithin(Duration.ofSeconds(TIMEOUT))
                  .extracting(SearchQueryResponse::items)
                  .satisfies(items -> Assertions.assertThat(items).isEmpty());
            });
  }

  /**
   * Deploys a process model and waits until it is accessible via the API.
   *
   * @return the process definition key
   */
  private long deployProcessModel(final BpmnModelInstance processModel) {
    final var deploymentEvent =
        client
            .newDeployResourceCommand()
            .addProcessModel(processModel, "test-process.bpmn")
            .send()
            .join();
    final var processDefinitionKey =
        deploymentEvent.getProcesses().getFirst().getProcessDefinitionKey();
    Awaitility.await("until process model is accessible via API")
        .atMost(Duration.ofSeconds(2 * TIMEOUT))
        .untilAsserted(
            () -> {
              final Future<?> futureRequest =
                  client.newProcessDefinitionGetRequest(processDefinitionKey).send();
              assertThat(futureRequest).succeedsWithin(Duration.ofSeconds(TIMEOUT));
            });
    return processDefinitionKey;
  }

  private long startProcess(final long processDefinitionKey) {
    final var processInstanceKey =
        client
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .send()
            .join()
            .getProcessInstanceKey();
    Awaitility.await("until process instance is created")
        .atMost(Duration.ofSeconds(2 * TIMEOUT))
        .untilAsserted(
            () -> {
              final Future<?> futureRequest =
                  client.newProcessInstanceGetRequest(processInstanceKey).send();
              assertThat(futureRequest).succeedsWithin(Duration.ofSeconds(TIMEOUT));
            });
    return processInstanceKey;
  }

  private void assertThatEntityNotFound(final Supplier<Future<?>> sendRequest) {
    Awaitility.await("until entity not found")
        .atMost(Duration.ofSeconds(2 * TIMEOUT))
        .untilAsserted(
            () ->
                assertThat(sendRequest.get())
                    .failsWithin(Duration.ofSeconds(TIMEOUT))
                    .withThrowableOfType(ExecutionException.class)
                    .extracting(Throwable::getCause)
                    .asInstanceOf(InstanceOfAssertFactories.type(ProblemException.class))
                    .extracting(ProblemException::details)
                    .asInstanceOf(InstanceOfAssertFactories.type(ProblemDetail.class))
                    .extracting(ProblemDetail::getStatus)
                    .isEqualTo(404));
  }

  private void assertThatChangesAreApplied(final PlannedOperationsResponse planChangeResponse) {
    Awaitility.await("until cluster purge completes")
        .untilAsserted(
            () ->
                Assertions.assertThatNoException()
                    .isThrownBy(() -> APPLICATION.healthActuator().ready()));
    Awaitility.await("until cluster is ready")
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                    .isComplete(1, 1, 1));
    Awaitility.await("until cluster is healthy")
        .untilAsserted(
            () -> TopologyAssert.assertThat(client.newTopologyRequest().send().join()).isHealthy());

    assertThat(planChangeResponse.getPlannedChanges()).isNotEmpty();
  }
}
