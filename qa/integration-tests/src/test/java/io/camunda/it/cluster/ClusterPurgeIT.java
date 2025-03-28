/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.cluster;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.ProblemDetail;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * TODO: We currently only test RDBMS, this should be tested by the ClusterPurgeMultiDbIT, once RDMS
 * works with the multi-db extension.
 */
@ZeebeIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ClusterPurgeIT {

  private static final int BROKER_COUNT = 1;
  private static final int PARTITION_COUNT = 1;
  private static final int REPLICATION_FACTOR = 1;

  private TestCluster cluster;
  private CamundaClient client;

  void setupCluster(final TestBrokerConfig config) {
    // Setup cluster based on the provided broker configuration
    cluster =
        TestCluster.builder()
            .withBrokersCount(BROKER_COUNT)
            .withPartitionsCount(PARTITION_COUNT)
            .withReplicationFactor(REPLICATION_FACTOR)
            .withEmbeddedGateway(true)
            .withBrokerConfig(config.brokerConfig)
            .build()
            .start()
            .awaitHealthyTopology();
    client = cluster.newClientBuilder().preferRestOverGrpc(true).build();
  }

  @AfterEach
  void tearDownCluster() {
    if (client != null) {
      client.close();
    }
    if (cluster != null) {
      cluster.close();
    }
  }

  @ParameterizedTest
  @MethodSource("brokerConfigs")
  void shouldPurgeProcessDefinitions(final TestBrokerConfig config) {
    // GIVEN
    setupCluster(config);
    final var processModel =
        Bpmn.createExecutableProcess("test-process").startEvent().endEvent().done();
    final var processDefinitionKey = deployProcessModel(processModel);
    final var actuator = ClusterActuator.of(cluster.availableGateway());

    // WHEN
    final var planChangeResponse = actuator.purge(false);

    // THEN
    assertThatChangesAreApplied(planChangeResponse);
    assertThatEntityNotFound(
        () -> client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send());
    assertThatEntityNotFound(
        () -> client.newProcessDefinitionGetRequest(processDefinitionKey).send());
  }

  @ParameterizedTest
  @MethodSource("brokerConfigs")
  void shouldPurgeProcessInstances(final TestBrokerConfig config) {
    // GIVEN
    setupCluster(config);
    final var processModel =
        Bpmn.createExecutableProcess("test-process")
            .startEvent()
            .serviceTask("service-task-1")
            .zeebeJobType("test")
            .endEvent()
            .done();
    final var processDefinitionKey = deployProcessModel(processModel);
    final var processInstanceKey = startProcess(processDefinitionKey);

    final var actuator = ClusterActuator.of(cluster.availableGateway());

    // WHEN
    final var planChangeResponse = actuator.purge(false);

    // THEN
    assertThatChangesAreApplied(planChangeResponse);
    assertThatEntityNotFound(() -> client.newCancelInstanceCommand(processInstanceKey).send());
    assertThatEntityNotFound(() -> client.newProcessInstanceGetRequest(processInstanceKey).send());
  }

  @ParameterizedTest
  @MethodSource("brokerConfigs")
  void shouldPurgeServiceTask(final TestBrokerConfig config) {
    // GIVEN
    setupCluster(config);
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

    final var actuator = ClusterActuator.of(cluster.availableGateway());

    // WHEN
    final var planChangeResponse = actuator.purge(false);

    // THEN
    assertThatChangesAreApplied(planChangeResponse);
    assertThatEntityNotFound(() -> client.newCompleteCommand(activeJob).send());
  }

  @ParameterizedTest
  @MethodSource("brokerConfigs")
  void shouldPurgeUserTask(final TestBrokerConfig config) {
    // GIVEN
    setupCluster(config);
    client = cluster.newClientBuilder().preferRestOverGrpc(true).build();
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
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              final Future<SearchResponse<UserTask>> userTaskFuture =
                  client.newUserTaskSearchRequest().send();
              Assertions.assertThat(userTaskFuture)
                  .succeedsWithin(Duration.ofSeconds(10))
                  .extracting(SearchResponse::items)
                  .satisfies(
                      items -> {
                        Assertions.assertThat(items).hasSize(1);
                        userTaskKey.set(items.getFirst().getUserTaskKey());
                      });
            });

    final var actuator = ClusterActuator.of(cluster.availableGateway());

    // WHEN
    final var planChangeResponse = actuator.purge(false);

    // THEN
    assertThatChangesAreApplied(planChangeResponse);
    assertThatEntityNotFound(() -> client.newUserTaskCompleteCommand(userTaskKey.get()).send());

    Awaitility.await("until user task query returns empty list")
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              final Future<SearchResponse<UserTask>> userTaskFuture =
                  client.newUserTaskSearchRequest().send();
              Assertions.assertThat(userTaskFuture)
                  .succeedsWithin(Duration.ofSeconds(10))
                  .extracting(SearchResponse::items)
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
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              final Future<?> futureRequest =
                  client.newProcessDefinitionGetRequest(processDefinitionKey).send();
              Assertions.assertThat(futureRequest).succeedsWithin(Duration.ofSeconds(10));
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
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              final Future<?> futureRequest =
                  client.newProcessInstanceGetRequest(processInstanceKey).send();
              Assertions.assertThat(futureRequest).succeedsWithin(Duration.ofSeconds(10));
            });
    return processInstanceKey;
  }

  private void assertThatEntityNotFound(final Supplier<Future<?>> sendRequest) {
    Awaitility.await("until entity not found")
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () ->
                Assertions.assertThat(sendRequest.get())
                    .failsWithin(Duration.ofSeconds(10))
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
                ClusterActuatorAssert.assertThat(cluster).hasCompletedChanges(planChangeResponse));
    Awaitility.await("until cluster is ready")
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                    .isComplete(BROKER_COUNT, PARTITION_COUNT, REPLICATION_FACTOR));
    Awaitility.await("until cluster is healthy")
        .untilAsserted(
            () -> TopologyAssert.assertThat(client.newTopologyRequest().send().join()).isHealthy());

    Assertions.assertThat(planChangeResponse.getPlannedChanges()).isNotEmpty();
  }

  private static Stream<TestBrokerConfig> brokerConfigs() {
    return Stream.of(
        new TestBrokerConfig("RdbmsExporter", TestStandaloneBroker::withRdbmsExporter)
        /*,
        new TestBrokerConfig(
            "CamundaExporter", t -> t.withCamundaExporter("http://localhost:9200", null))*/ );
  }

  static class TestBrokerConfig {
    private final String name;
    private final Consumer<TestStandaloneBroker> brokerConfig;

    TestBrokerConfig(final String name, final Consumer<TestStandaloneBroker> brokerConfig) {
      this.name = name;
      this.brokerConfig = brokerConfig;
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
