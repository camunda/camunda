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
import static io.camunda.it.util.TestHelper.*;
import static io.camunda.it.util.TestHelper.activateAndCompleteJobs;
import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static io.camunda.zeebe.test.util.testcontainers.TestSearchContainers.CAMUNDA_DATABASE;
import static io.camunda.zeebe.test.util.testcontainers.TestSearchContainers.CAMUNDA_PASSWORD;
import static io.camunda.zeebe.test.util.testcontainers.TestSearchContainers.CAMUNDA_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
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
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;

/**
 * Rolling update test for secondary storage across mixed-version upgrades and rollbacks.
 *
 * <p>The test drives a representative scenario across a rolling-update version boundary: it creates
 * identity resources, starts a process instance, advances it through a service task (job), message
 * subscription, and user task, then cancels the process instance via a batch operation. This
 * exercises secondary-storage visibility for several entity types while brokers run on different
 * versions.
 *
 * <p>The same scenario is parameterized across all supported secondary storage implementations
 * (RDBMS, Elasticsearch, and OpenSearch) to avoid duplicated test logic.
 */
final class SecondaryStorageRollingUpdateIT {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(SecondaryStorageRollingUpdateIT.class);

  // ---------------------------------------------------------------------------
  // Process model
  // ---------------------------------------------------------------------------

  private static final String PROCESS_ID = "rolling-update-process";
  private static final String MESSAGE_NAME = "approval-message";
  private static final String CORRELATION_KEY_VAR = "correlationKey";
  private static final String PREPARE_JOB_TYPE = "prepare";
  private static final String FINALIZE_JOB_TYPE = "finalize";

  /**
   * Process model used to cover process-related secondary-storage entity types:
   *
   * <ul>
   *   <li>Process definition metadata → version tag and custom property
   *   <li>Service tasks → jobs
   *   <li>Input/output variable mappings → variables
   *   <li>Intermediate message catch event → message subscription
   *   <li>User task (Zeebe native) → user task
   * </ul>
   *
   * <pre>
   * start → serviceTask("prepare") → catchEvent("waitForApproval") → userTask("review")
   *       → serviceTask("finalize") → end
   * </pre>
   *
   * <p>The test completes the user task and leaves the final service task active so the process
   * instance can be cancelled via a batch operation.
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
              e ->
                  e.message(
                      m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_KEY_VAR)))
          .userTask(
              "review",
              u -> u.zeebeUserTask().zeebeAssignee("demo").zeebeCandidateGroups("reviewers"))
          .serviceTask("finalize", t -> t.zeebeJobType(FINALIZE_JOB_TYPE))
          .endEvent()
          .done();

  // ---------------------------------------------------------------------------
  // Test matrix
  // ---------------------------------------------------------------------------

  private static final List<StorageTestCase> STORAGE_TEST_CASES =
      List.of(
          StorageTestCase.rdbms(), StorageTestCase.elasticsearch(), StorageTestCase.opensearch());

  static Stream<Arguments> versionAndStorageMatrix() throws IOException, InterruptedException {
    final List<String> versions = ExporterMigrationTestHelper.fetchAllPatchesFromPreviousMinor();

    return versions.stream()
        .flatMap(
            fromVersion ->
                STORAGE_TEST_CASES.stream()
                    .map(storage -> Arguments.of(fromVersion, "CURRENT", storage)));
  }

  static Stream<Arguments> versionAndStorageMatrixLocal() throws IOException, InterruptedException {
    final List<String> versions = ExporterMigrationTestHelper.fetchAllPatchesFromPreviousMinor();

    return versions.stream()
        .map(fromVersion -> Arguments.of(fromVersion, "SNAPSHOT", StorageTestCase.elasticsearch()));
  }

  // ---------------------------------------------------------------------------
  // Test
  // ---------------------------------------------------------------------------

  @ParameterizedTest(name = "storage={2}, from {0} to {1}", allowZeroInvocations = true)
  @Tag("dl-nightly")
  @MethodSource("versionAndStorageMatrix")
  void shouldPreserveSecondaryStorageEntitiesDuringRollingUpdate(
      final String from, final String to, final StorageTestCase storage) {
    final Network network = Network.newNetwork();
    final GenericContainer<?> storageContainer = storage.newContainer(network);
    final CamundaCluster cluster = storage.newCluster(network);
    final Collection<CamundaVolume> volumes = new LinkedList<>();
    BrokerNode<?> oldVersionBroker = null;
    BrokerNode<?> newVersionBroker = null;
    final var oldVersionBrokerLogs = new ContainerLogBuffer();
    final var newVersionBrokerLogs = new ContainerLogBuffer();

    try {
      LOGGER.info("Starting storage container for {}", storage.name);
      storageContainer.start();

      LOGGER.info("Initializing broker config");
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
      oldVersionBroker = cluster.getBrokers().get(oldNodeId);
      newVersionBroker = cluster.getBrokers().get(newNodeId);
      oldVersionBroker.self().withLogConsumer(oldVersionBrokerLogs);
      newVersionBroker.self().withLogConsumer(newVersionBrokerLogs);
      final GatewayNode<?> oldVersionGateway = cluster.getGateways().get(String.valueOf(oldNodeId));
      final GatewayNode<?> newVersionGateway = cluster.getGateways().get(String.valueOf(newNodeId));

      updateBroker(oldVersionBroker, oldNodeId, from, storage);
      updateBroker(newVersionBroker, newNodeId, from, storage);

      // === Phase 1: both brokers on old version — deploy, start, drive to message subscription ===
      LOGGER.info("Starting brokers");
      final CompletableFuture<Void> oldVersionStartFuture =
          CompletableFuture.runAsync(oldVersionBroker::start);
      awaitSecondOldBrokerStartDelay();
      newVersionBroker.start();
      oldVersionStartFuture.join();

      LOGGER.info("Started brokers");

      try (final var client = newClient(oldVersionGateway)) {
        LOGGER.info("Deploying process definition");
        deployProcessAndWaitForIt(client, RICH_PROCESS, "rolling-update-process.bpmn")
            .getProcessDefinitionKey();
        LOGGER.info("Deployed process definition");

        // process instance are immediately visible
        LOGGER.info("=== Test Run 1 - Old cluster version ===");
        testCluster(client);
      }

      // === Phase 2: upgrade new broker, correlate message, user task becomes active ===
      LOGGER.info("Stopping one broker to for upgrade");
      newVersionBroker.stop();
      LOGGER.info("Upgrading broker to new version");
      updateBroker(newVersionBroker, newNodeId, to, storage);
      newVersionBroker.start();

      try (final var client = newClient(newVersionGateway)) {
        // create a second instance to exercise writes on the mixed-version cluster
        LOGGER.info("=== Test Run 2 - New cluster version ===");
        testCluster(client);
      }

      // === Phase 3: new broker restarts on old schema — complete process, verify new writes ===
      LOGGER.info("Stopping new broker to complete rollback");
      newVersionBroker.stop();
      updateBroker(newVersionBroker, newNodeId, from, storage);
      newVersionBroker.start();

      try (final var client = newClient(oldVersionGateway)) {
        // create a third instance to prove new writes work on the updated schema
        LOGGER.info("=== Test Run 3 - Old cluster version again ===");
        testCluster(client);
      }
    } catch (final RuntimeException | Error e) {
      LOGGER.error("Test failed, dumping broker logs before rethrowing", e);
      logBrokerContainerLogs("oldVersionBroker", oldVersionBroker, oldVersionBrokerLogs);
      logBrokerContainerLogs("newVersionBroker", newVersionBroker, newVersionBrokerLogs);
      throw e;
    } finally {
      LOGGER.info("Stopping all components");
      cluster.stop();
      storageContainer.stop();
      CloseHelper.closeAll(volumes);
      network.close();
      LOGGER.info("Stopped all components");
    }
  }

  private void testCluster(final CamundaClient client) {
    final String correlationKey = UUID.randomUUID().toString();
    // short 8-char suffix for readable, unique identity object IDs per test-cluster call
    final String suffix = correlationKey.replace("-", "").substring(0, 8);
    final String username = "ru-user-" + suffix;
    final String roleId = "ru-role-" + suffix;
    final String groupId = "ru-group-" + suffix;
    final String tenantId = "ru-" + suffix;

    // --- identity objects ---
    LOGGER.info("Creating user and await secondary storage");
    client
        .newCreateUserCommand()
        .username(username)
        .name("RU Test User")
        .email(username + "@example.com")
        .password("Test1234!")
        .send()
        .join();
    waitForUser(client, username);

    LOGGER.info("Creating role and await secondary storage");
    client.newCreateRoleCommand().roleId(roleId).name("RU Test Role").send().join();
    waitForRole(client, roleId);

    LOGGER.info("Creating group and await secondary storage");
    client.newCreateGroupCommand().groupId(groupId).name("RU Test Group").send().join();
    waitForGroup(client, groupId);

    LOGGER.info("Creating tenant and await secondary storage");
    client.newCreateTenantCommand().tenantId(tenantId).name("RU Test Tenant").send().join();
    waitForTenant(client, tenantId);

    // --- process instance ---
    LOGGER.info("Starting process instance");
    final long piKey =
        await()
            // sometimes we have to wait on cluster sync, otherwise get
            // Command 'CREATE' rejected with code 'NOT_FOUND'
            .ignoreException(ClientStatusException.class)
            .until(
                () ->
                    startProcessInstance(
                            client,
                            PROCESS_ID,
                            Map.of(CORRELATION_KEY_VAR, correlationKey, "inputData", "hello"))
                        .getProcessInstanceKey(),
                Objects::nonNull);

    LOGGER.info("Waiting for process instance");
    waitForProcessInstancesToStart(client, f -> f.processInstanceKey(piKey), 1);
    // initial variables set at instance creation
    LOGGER.info("Waiting for variables");
    waitUntilProcessInstanceHasVariable(
        client, piKey, CORRELATION_KEY_VAR, "\"" + correlationKey + "\"");
    waitUntilProcessInstanceHasVariable(client, piKey, "inputData", "\"hello\"");

    // complete the "prepare" job — process parks at the message catch event
    LOGGER.info("Waiting to activate job");
    activateAndCompleteJobs(client, PREPARE_JOB_TYPE, "rolling-update-worker", 1);

    // output variable written by the job and message subscription now active
    LOGGER.info("Waiting for message subscription");
    waitForMessageSubscriptions(client, f -> f.processInstanceKey(piKey), 1);
    LOGGER.info("Correlating message");
    client
        .newCorrelateMessageCommand()
        .messageName(MESSAGE_NAME)
        .correlationKey(correlationKey)
        .execute();

    // process advances to user task — complete it, leaving the finalize job pending
    LOGGER.info("Waiting for user task");
    awaitAndCompleteUserTaskInSecondaryStorage(client, piKey);
  }

  // ---------------------------------------------------------------------------
  // Await helpers — verify entity visibility in secondary storage
  // ---------------------------------------------------------------------------

  /**
   * The internal API of the camunda-client for user-task search was changed from 8.9 to 8.10, so we
   * cannot search for user-tasks via processInstanceKey so easily. We need a special method here
   * for it.
   */
  private void awaitAndCompleteUserTaskInSecondaryStorage(
      final CamundaClient client, final long processInstanceKey) {
    await(
            "user task for process instance %d visible in secondary storage"
                .formatted(processInstanceKey))
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var result = client.newUserTaskSearchRequest().send().join();
              final var singleResult =
                  result.items().stream()
                      .filter(ut -> ut.getProcessInstanceKey() == processInstanceKey)
                      .findFirst();
              assertThat(singleResult)
                  .as(
                      "user task for process instance %d should be present in secondary storage",
                      processInstanceKey)
                  .isPresent();
            });
    // complete after confirmed present — do not put inside untilAsserted (would retry on failure)
    final var result = client.newUserTaskSearchRequest().send().join();
    final var userTask =
        result.items().stream()
            .filter(ut -> ut.getProcessInstanceKey() == processInstanceKey)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "User task for process instance %d not found"
                            .formatted(processInstanceKey)));
    client.newCompleteUserTaskCommand(userTask.getUserTaskKey()).send().join();
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
    await(
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

  private void logBrokerContainerLogs(
      final String brokerName, final BrokerNode<?> broker, final ContainerLogBuffer logBuffer) {
    try {
      final var container = broker == null ? null : broker.self();
      final var containerId = container == null ? "<not-created>" : container.getContainerId();
      final var imageName = container == null ? "<not-created>" : container.getDockerImageName();
      LOGGER.error(
          "{} container logs after test failure (containerId={}, image={}):\n{}",
          brokerName,
          containerId,
          imageName,
          logBuffer);
    } catch (final RuntimeException logFailure) {
      LOGGER.error("Failed to log {} container logs", brokerName, logFailure);
    }
  }

  private static final class ContainerLogBuffer implements Consumer<OutputFrame> {

    private final StringBuilder buffer = new StringBuilder();

    @Override
    public void accept(final OutputFrame outputFrame) {
      synchronized (buffer) {
        buffer.append(outputFrame.getUtf8String());
      }
    }

    @Override
    public String toString() {
      synchronized (buffer) {
        if (buffer.isEmpty()) {
          return "<no logs captured>";
        }

        return buffer.toString();
      }
    }
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
          TestSearchContainers::createDefaultElasticsearchContainer,
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
    public String toString() {
      return name;
    }
  }
}
