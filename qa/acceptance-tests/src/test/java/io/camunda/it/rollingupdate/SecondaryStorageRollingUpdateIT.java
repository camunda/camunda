/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rollingupdate;

import static io.camunda.application.commons.security.CamundaSecurityConfiguration.AUTHORIZATION_CHECKS_ENV_VAR;
import static io.camunda.application.commons.security.CamundaSecurityConfiguration.UNPROTECTED_API_ENV_VAR;
import static io.camunda.it.util.TestHelper.activateAndCompleteJobs;
import static io.camunda.it.util.TestHelper.completeUserTask;
import static io.camunda.it.util.TestHelper.publishMessage;
import static io.camunda.zeebe.test.util.testcontainers.TestSearchContainers.CAMUNDA_DATABASE;
import static io.camunda.zeebe.test.util.testcontainers.TestSearchContainers.CAMUNDA_PASSWORD;
import static io.camunda.zeebe.test.util.testcontainers.TestSearchContainers.CAMUNDA_USER;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.container.ClusterHelper;
import io.camunda.container.cluster.BrokerNode;
import io.camunda.container.cluster.CamundaCluster;
import io.camunda.container.cluster.GatewayNode;
import io.camunda.container.volume.CamundaVolume;
import io.camunda.it.schema.ExporterMigrationTestHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

/**
 * Rolling update test for secondary storage across mixed-version upgrades.
 *
 * <p>The test drives a richer process through multiple lifecycle stages — service task (job),
 * message subscription, and user task — to exercise INSERT and UPDATE paths for all major
 * secondary-storage entity types across a rolling-update version boundary.
 *
 * <p>The same scenario is parameterized across all supported secondary storage implementations
 * (RDBMS, Elasticsearch, and OpenSearch) to avoid duplicated test logic.
 */
final class SecondaryStorageRollingUpdateIT {

  // ---------------------------------------------------------------------------
  // Process model
  // ---------------------------------------------------------------------------

  private static final String PROCESS_ID = "rolling-update-process";
  private static final String MESSAGE_NAME = "approval-message";
  private static final String CORRELATION_KEY_VAR = "correlationKey";
  private static final String PREPARE_JOB_TYPE = "prepare";
  private static final String FINALIZE_JOB_TYPE = "finalize";

  /**
   * A process that covers the main secondary-storage entity types:
   *
   * <ul>
   *   <li>Service task → job
   *   <li>Input/output variable mappings → variables
   *   <li>Intermediate message catch event → message subscription
   *   <li>User task (Zeebe native) → user task
   *   <li>Version tag on process definition → tags
   * </ul>
   *
   * <pre>
   * start → serviceTask("prepare") → catchEvent("waitForApproval") → userTask("review")
   *       → serviceTask("finalize") → end
   * </pre>
   */
  private static final BpmnModelInstance RICH_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .versionTag("v1.0")
          .zeebeProperty("owner", "test-rolling-update")
          .startEvent()
          .serviceTask(
              "prepare",
              t ->
                  t.zeebeJobType(PREPARE_JOB_TYPE)
                      .zeebeInput(CORRELATION_KEY_VAR, "corrKey")
                      .zeebeOutputExpression("\"done\"", "prepareResult"))
          .intermediateCatchEvent(
              "waitForApproval",
              e -> e.message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression("corrKey")))
          .userTask(
              "review",
              u -> u.zeebeUserTask().zeebeAssignee("demo").zeebeCandidateGroups("reviewers"))
          .serviceTask("finalize", t -> t.zeebeJobType(FINALIZE_JOB_TYPE))
          .endEvent()
          .done();

  // ---------------------------------------------------------------------------
  // Test matrix
  // ---------------------------------------------------------------------------

  /**
   * When set to {@code true} (via {@code -DrollingUpdateTest.devMode=true}), the matrix is reduced
   * to a single combination: RDBMS storage × the latest patch of the previous minor version. This
   * makes local development iteration significantly faster without spinning up all three storage
   * backends across all previous patch versions.
   */
  private static final boolean DEV_MODE = Boolean.getBoolean("rollingUpdateTest.devMode");

  private static final List<StorageTestCase> STORAGE_TEST_CASES =
      List.of(
          StorageTestCase.rdbms(), StorageTestCase.elasticsearch(), StorageTestCase.opensearch());

  static Stream<Arguments> versionAndStorageMatrix() throws IOException, InterruptedException {
    final List<String> versions =
        DEV_MODE
            ? ExporterMigrationTestHelper.fetchLatestPatchFromPreviousMinor()
            : ExporterMigrationTestHelper.fetchAllPatchesFromPreviousMinor();

    final List<StorageTestCase> storages =
        DEV_MODE ? List.of(StorageTestCase.rdbms()) : STORAGE_TEST_CASES;

    return versions.stream()
        .flatMap(
            fromVersion -> storages.stream().map(storage -> Arguments.of(fromVersion, storage)));
  }

  // ---------------------------------------------------------------------------
  // Test
  // ---------------------------------------------------------------------------

  @ParameterizedTest(name = "storage={1}, from {0} to CURRENT", allowZeroInvocations = true)
  @Tag("dl-nightly")
  @MethodSource("versionAndStorageMatrix")
  void shouldPreserveSecondaryStorageEntitiesDuringRollingUpdate(
      final String from, final StorageTestCase storage) {
    final String to = "SNAPSHOT";

    final Network network = Network.newNetwork();
    final GenericContainer<?> storageContainer = storage.newContainer(network);
    final CamundaCluster cluster = storage.newCluster(network);
    final Collection<CamundaVolume> volumes = new LinkedList<>();

    try {
      storageContainer.start();

      final var initialContactPoints =
          cluster.getBrokers().values().stream()
              .map(BrokerNode::getInternalClusterAddress)
              .toList();
      cluster
          .getBrokers()
          .values()
          .forEach(broker -> configureBroker(broker, initialContactPoints, volumes, storage));

      final int oldNodeId = 0;
      final int newNodeId = 1;
      final BrokerNode<?> oldVersionBroker = cluster.getBrokers().get(oldNodeId);
      final BrokerNode<?> newVersionBroker = cluster.getBrokers().get(newNodeId);
      final GatewayNode<?> oldVersionGateway = cluster.getGateways().get(String.valueOf(oldNodeId));
      final GatewayNode<?> newVersionGateway = cluster.getGateways().get(String.valueOf(newNodeId));

      updateBroker(oldVersionBroker, oldNodeId, from, storage);
      updateBroker(newVersionBroker, newNodeId, from, storage);

      // === Phase 1: both brokers on old version — deploy, start, drive to message subscription ===
      final CompletableFuture<Void> oldVersionStartFuture =
          CompletableFuture.runAsync(oldVersionBroker::start);
      awaitSecondOldBrokerStartDelay();
      newVersionBroker.start();
      oldVersionStartFuture.join();

      final long processDefinitionKey;
      final long phase1Key;
      final String correlationKey = UUID.randomUUID().toString();

      try (final var client = newClient(oldVersionGateway)) {
        processDefinitionKey = deployRichProcess(client);
        phase1Key = createRichProcessInstance(client, correlationKey);

        // process definition and instance are immediately visible
        awaitProcessDefinitionInSecondaryStorage(client, processDefinitionKey);
        awaitProcessInstanceInSecondaryStorage(client, phase1Key);
        // initial variables set at instance creation
        awaitVariableInSecondaryStorage(client, phase1Key, CORRELATION_KEY_VAR);
        awaitVariableInSecondaryStorage(client, phase1Key, "inputData");

        // complete the "prepare" job — process parks at the message catch event
        activateAndCompleteJobs(client, PREPARE_JOB_TYPE, "rolling-update-worker", 1);

        // output variable written by the job and message subscription now active
        awaitVariableInSecondaryStorage(client, phase1Key, "prepareResult");
        awaitMessageSubscriptionInSecondaryStorage(client, phase1Key, MESSAGE_NAME);
      }

      // === Phase 2: upgrade new broker, stop old — correlate message, user task becomes active ===
      newVersionBroker.stop();
      updateBroker(newVersionBroker, newNodeId, to, storage);
      newVersionBroker.start();
      oldVersionBroker.stop();

      final long phase2Key;
      try (final var client = newClient(newVersionGateway)) {
        // entities written in Phase 1 must survive the version boundary
        awaitProcessDefinitionInSecondaryStorage(client, processDefinitionKey);
        awaitProcessInstanceInSecondaryStorage(client, phase1Key);
        awaitVariableInSecondaryStorage(client, phase1Key, CORRELATION_KEY_VAR);
        awaitVariableInSecondaryStorage(client, phase1Key, "prepareResult");
        awaitMessageSubscriptionInSecondaryStorage(client, phase1Key, MESSAGE_NAME);

        // unblock the message catch event → user task becomes active
        publishMessage(client, MESSAGE_NAME, correlationKey);
        awaitUserTaskInSecondaryStorage(client, phase1Key);

        // create a second instance to exercise writes on the mixed-version cluster
        phase2Key = createRichProcessInstance(client, UUID.randomUUID().toString());
        awaitProcessInstanceInSecondaryStorage(client, phase2Key);
      }

      // === Phase 3: old broker restarts on new schema — complete process, verify new writes ===
      oldVersionBroker.start();
      newVersionBroker.stop();

      try (final var client = newClient(oldVersionGateway)) {
        // all prior entities must still be queryable on the updated schema
        awaitProcessDefinitionInSecondaryStorage(client, processDefinitionKey);
        awaitProcessInstanceInSecondaryStorage(client, phase1Key);
        awaitProcessInstanceInSecondaryStorage(client, phase2Key);
        awaitVariableInSecondaryStorage(client, phase1Key, CORRELATION_KEY_VAR);
        awaitVariableInSecondaryStorage(client, phase1Key, "prepareResult");

        // complete the user task and finalize the process
        completeUserTask(client, phase1Key);
        activateAndCompleteJobs(client, FINALIZE_JOB_TYPE, "rolling-update-worker", 1);

        // create a third instance to prove new writes work on the updated schema
        final long phase3Key = createRichProcessInstance(client, UUID.randomUUID().toString());
        awaitProcessInstanceInSecondaryStorage(client, phase3Key);
      }
    } finally {
      cluster.stop();
      storageContainer.stop();
      CloseHelper.closeAll(volumes);
      network.close();
    }
  }

  // ---------------------------------------------------------------------------
  // Process helpers
  // ---------------------------------------------------------------------------

  private long deployRichProcess(final CamundaClient client) {
    final var deployment =
        client
            .newDeployResourceCommand()
            .addProcessModel(RICH_PROCESS, "rolling-update-process.bpmn")
            .send()
            .join(30, TimeUnit.SECONDS);
    return deployment.getProcesses().getFirst().getProcessDefinitionKey();
  }

  private long createRichProcessInstance(final CamundaClient client, final String correlationKey) {
    return Awaitility.await("process instance creation")
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(100))
        .ignoreExceptions()
        .until(
            () ->
                client
                    .newCreateInstanceCommand()
                    .bpmnProcessId(PROCESS_ID)
                    .latestVersion()
                    .variables(Map.of(CORRELATION_KEY_VAR, correlationKey, "inputData", "hello"))
                    .send()
                    .join(),
            Objects::nonNull)
        .getProcessInstanceKey();
  }

  // ---------------------------------------------------------------------------
  // Await helpers — verify entity visibility in secondary storage
  // ---------------------------------------------------------------------------

  private void awaitProcessDefinitionInSecondaryStorage(
      final CamundaClient client, final long processDefinitionKey) {
    Awaitility.await(
            "process definition %d visible in secondary storage".formatted(processDefinitionKey))
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              final var result =
                  client
                      .newProcessDefinitionSearchRequest()
                      .filter(f -> f.processDefinitionKey(processDefinitionKey))
                      .send()
                      .join();
              assertThat(result.items())
                  .as(
                      "process definition %d should be present in secondary storage",
                      processDefinitionKey)
                  .hasSize(1);
            });
  }

  private void awaitProcessInstanceInSecondaryStorage(
      final CamundaClient client, final long processInstanceKey) {
    Awaitility.await(
            "process instance %d visible in secondary storage".formatted(processInstanceKey))
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              final var result =
                  client
                      .newProcessInstanceSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey))
                      .send()
                      .join();
              assertThat(result.items())
                  .as(
                      "process instance %d should be present in secondary storage",
                      processInstanceKey)
                  .hasSize(1);
            });
  }

  private void awaitVariableInSecondaryStorage(
      final CamundaClient client, final long processInstanceKey, final String variableName) {
    Awaitility.await(
            "variable '%s' for process instance %d visible in secondary storage"
                .formatted(variableName, processInstanceKey))
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              final var result =
                  client
                      .newVariableSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey).name(variableName))
                      .send()
                      .join();
              assertThat(result.items())
                  .as(
                      "variable '%s' for process instance %d should be present in secondary storage",
                      variableName, processInstanceKey)
                  .hasSize(1);
            });
  }

  private void awaitMessageSubscriptionInSecondaryStorage(
      final CamundaClient client, final long processInstanceKey, final String messageName) {
    Awaitility.await(
            "message subscription '%s' for process instance %d visible in secondary storage"
                .formatted(messageName, processInstanceKey))
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              final var result =
                  client
                      .newMessageSubscriptionSearchRequest()
                      .filter(
                          f -> f.processInstanceKey(processInstanceKey).messageName(messageName))
                      .send()
                      .join();
              assertThat(result.items())
                  .as(
                      "message subscription '%s' for process instance %d should be present in secondary storage",
                      messageName, processInstanceKey)
                  .hasSize(1);
            });
  }

  private void awaitUserTaskInSecondaryStorage(
      final CamundaClient client, final long processInstanceKey) {
    Awaitility.await(
            "user task for process instance %d visible in secondary storage"
                .formatted(processInstanceKey))
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              final var result =
                  client
                      .newUserTaskSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey))
                      .send()
                      .join();
              assertThat(result.items())
                  .as(
                      "user task for process instance %d should be present in secondary storage",
                      processInstanceKey)
                  .hasSize(1);
            });
  }

  // ---------------------------------------------------------------------------
  // Infrastructure helpers
  // ---------------------------------------------------------------------------

  private void updateBroker(
      final BrokerNode<?> broker,
      final int id,
      final String version,
      final StorageTestCase storage) {
    ClusterHelper.updateBroker(broker, id, version);
    applySecondaryStorageEnv(broker, storage);
  }

  private void awaitSecondOldBrokerStartDelay() {
    final long delayStartNanos = System.nanoTime();
    Awaitility.await(
            "wait before starting second old-version broker to allow for bootstrapping liquibase without race condition")
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                Duration.ofNanos(System.nanoTime() - delayStartNanos)
                        .compareTo(Duration.ofSeconds(3))
                    >= 0);
  }

  private void configureBroker(
      final BrokerNode<?> broker,
      final List<String> initialContactPoints,
      final Collection<CamundaVolume> volumes,
      final StorageTestCase storage) {
    ClusterHelper.configureBroker(broker, initialContactPoints, volumes);
    applySecondaryStorageEnv(broker, storage);
  }

  private void applySecondaryStorageEnv(final BrokerNode<?> broker, final StorageTestCase storage) {
    final var type = storage.type().name();
    broker
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", type)
        .withEnv(
            "CAMUNDA_DATA_SECONDARYSTORAGE_%s_URL".formatted(type.toUpperCase()), storage.url());

    if (storage.username() != null) {
      broker.withEnv(
          "CAMUNDA_DATA_SECONDARYSTORAGE_%s_USERNAME".formatted(type.toUpperCase()),
          storage.username());
    }

    if (storage.password() != null) {
      broker.withEnv(
          "CAMUNDA_DATA_SECONDARYSTORAGE_%s_PASSWORD".formatted(type.toUpperCase()),
          storage.password());
    }
  }

  private static CamundaClient newClient(final GatewayNode<?> gateway) {
    return CamundaClient.newClientBuilder()
        .preferRestOverGrpc(false)
        .grpcAddress(gateway.getGrpcAddress())
        .restAddress(gateway.getRestAddress())
        .build();
  }

  // ---------------------------------------------------------------------------
  // Storage test cases
  // ---------------------------------------------------------------------------

  private record StorageTestCase(
      String name,
      SecondaryStorageType type,
      String networkAlias,
      int port,
      Supplier<GenericContainer<?>> containerSupplier,
      String username,
      String password) {

    static StorageTestCase rdbms() {
      return new StorageTestCase(
          "rdbms",
          SecondaryStorageType.rdbms,
          "postgres",
          5432,
          TestSearchContainers::createDefaultPostgresContainer,
          CAMUNDA_USER,
          CAMUNDA_PASSWORD);
    }

    static StorageTestCase elasticsearch() {
      return new StorageTestCase(
          "elasticsearch",
          SecondaryStorageType.elasticsearch,
          "elasticsearch",
          9200,
          TestSearchContainers::createDefeaultElasticsearchContainer,
          null,
          null);
    }

    static StorageTestCase opensearch() {
      return new StorageTestCase(
          "opensearch",
          SecondaryStorageType.opensearch,
          "opensearch",
          9200,
          TestSearchContainers::createDefaultOpensearchContainer,
          null,
          null);
    }

    GenericContainer<?> newContainer(final Network network) {
      return containerSupplier.get().withNetwork(network).withNetworkAliases(networkAlias);
    }

    CamundaCluster newCluster(final Network network) {
      return CamundaCluster.builder()
          .withNetwork(network)
          .withEmbeddedGateway(true)
          .withBrokersCount(2)
          .withPartitionsCount(2)
          .withReplicationFactor(1)
          .withNodeConfig(
              node ->
                  node.withUnifiedConfig(
                          cfg -> {
                            cfg.getSystem().getUpgrade().setEnableVersionCheck(false);
                            cfg.getData().getSecondaryStorage().setType(type);

                            switch (type) {
                              case rdbms -> {
                                cfg.getData().getSecondaryStorage().getRdbms().setUrl(url());
                                cfg.getData()
                                    .getSecondaryStorage()
                                    .getRdbms()
                                    .setUsername(CAMUNDA_USER);
                                cfg.getData()
                                    .getSecondaryStorage()
                                    .getRdbms()
                                    .setPassword(CAMUNDA_PASSWORD);
                              }
                              case elasticsearch ->
                                  cfg.getData()
                                      .getSecondaryStorage()
                                      .getElasticsearch()
                                      .setUrl(url());
                              case opensearch ->
                                  cfg.getData().getSecondaryStorage().getOpensearch().setUrl(url());
                              case none ->
                                  throw new IllegalStateException(
                                      "storage type 'none' is not supported");
                              default ->
                                  throw new IllegalStateException(
                                      "storage type '%s' is not supported".formatted(type));
                            }
                          })
                      .withEnv(UNPROTECTED_API_ENV_VAR, "true")
                      .withEnv(AUTHORIZATION_CHECKS_ENV_VAR, "false"))
          .build();
    }

    String url() {
      return type == SecondaryStorageType.rdbms
          ? "jdbc:postgresql://%s:%d/%s".formatted(networkAlias, port, CAMUNDA_DATABASE)
          : "http://%s:%d".formatted(networkAlias, port);
    }

    @Override
    public @NonNull String toString() {
      return name;
    }
  }
}
