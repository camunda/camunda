/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test;

import static io.camunda.application.commons.security.CamundaSecurityConfiguration.AUTHORIZATION_CHECKS_ENV_VAR;
import static io.camunda.application.commons.security.CamundaSecurityConfiguration.UNPROTECTED_API_ENV_VAR;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.container.cluster.BrokerNode;
import io.camunda.container.cluster.CamundaCluster;
import io.camunda.container.cluster.CamundaClusterBuilder;
import io.camunda.container.cluster.GatewayNode;
import io.camunda.container.volume.CamundaVolume;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.camunda.zeebe.util.SemanticVersion;
import io.camunda.zeebe.util.VersionUtil;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

/**
 * Rolling update test for secondary storage across mixed-version upgrades.
 *
 * <p>The same scenario is parameterized across all supported secondary storage implementations
 * (RDBMS, Elasticsearch, and OpenSearch) to avoid duplicated test logic.
 */
final class SecondaryStorageRollingUpdateTest {
  private static final Duration SECOND_OLD_BROKER_START_DELAY = Duration.ofSeconds(3);

  private static List<Arguments> cachedVersionMatrix;

  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

  private static final String CAMUNDA_DATABASE = "camunda";
  private static final String CAMUNDA_USER = "camunda";
  private static final String CAMUNDA_PASSWORD = "Camunda_Pass123!";

  private static final List<StorageTestCase> STORAGE_TEST_CASES =
      List.of(
          StorageTestCase.rdbms(), StorageTestCase.elasticsearch(), StorageTestCase.opensearch());

  static Stream<Arguments> versionAndStorageMatrix() {
    if (cachedVersionMatrix == null) {
      cachedVersionMatrix = VersionCompatibilityMatrix.auto().toList();
    }

    return cachedVersionMatrix.stream()
        .flatMap(
            versionArgs -> {
              final var arguments = versionArgs.get();
              final var from = arguments[0].toString();
              final var to = arguments[1].toString();
              return STORAGE_TEST_CASES.stream().map(storage -> Arguments.of(from, to, storage));
            });
  }

  @ParameterizedTest(name = "storage={2}, from {0} to {1}", allowZeroInvocations = true)
  @MethodSource("versionAndStorageMatrix")
  void shouldPreserveProcessInstancesInSecondaryStorageDuringRollingUpdate(
      final String from, final String to, final StorageTestCase storage) {

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

      // given: cluster has two nodes with fixed versions
      updateBroker(oldVersionBroker, oldNodeId, from, storage);
      updateBroker(newVersionBroker, newNodeId, from, storage);

      // --- Phase 1: start both nodes on old version with staggered startup ---
      final CompletableFuture<Void> oldVersionStartFuture =
          CompletableFuture.runAsync(oldVersionBroker::start);
      awaitSecondOldBrokerStartDelay();
      newVersionBroker.start();
      oldVersionStartFuture.join();
      final long phase1Key;
      try (final var client = newClient(oldVersionGateway)) {
        deployProcess(client);
        phase1Key =
            Awaitility.await("phase 1 process instance creation")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(100))
                .ignoreExceptions()
                .until(() -> createProcessInstance(client), Objects::nonNull)
                .getProcessInstanceKey();

        awaitProcessInstanceInSecondaryStorage(client, phase1Key);
      }

      // --- Phase 2: upgrade ---
      newVersionBroker.stop();
      updateBroker(newVersionBroker, newNodeId, to, storage);
      newVersionBroker.start();
      oldVersionBroker.stop();

      final long phase2Key;
      try (final var phase2Client = newClient(newVersionGateway)) {
        phase2Key =
            Awaitility.await("phase 2 process instance creation")
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(100))
                .ignoreExceptions()
                .until(() -> createProcessInstance(phase2Client), Objects::nonNull)
                .getProcessInstanceKey();

        awaitProcessInstanceInSecondaryStorage(phase2Client, phase1Key);
        awaitProcessInstanceInSecondaryStorage(phase2Client, phase2Key);
      }

      // --- Phase 3: old node on new schema ---
      oldVersionBroker.start();
      newVersionBroker.stop();

      try (final var phase3Client = newClient(oldVersionGateway)) {
        final long phase3Key =
            Awaitility.await("phase 3 process instance creation")
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(100))
                .ignoreExceptions()
                .until(() -> createProcessInstance(phase3Client), Objects::nonNull)
                .getProcessInstanceKey();

        awaitProcessInstanceInSecondaryStorage(phase3Client, phase1Key);
        awaitProcessInstanceInSecondaryStorage(phase3Client, phase2Key);
        awaitProcessInstanceInSecondaryStorage(phase3Client, phase3Key);
      }
    } finally {
      cluster.stop();
      storageContainer.stop();
      CloseHelper.closeAll(volumes);
      network.close();
    }
  }

  private void updateBroker(
      final BrokerNode<?> broker,
      final int id,
      final String version,
      final StorageTestCase storage) {
    if ("CURRENT".equals(version)) {
      broker.setDockerImageName(
          ZeebeTestContainerDefaults.defaultTestImage().asCanonicalNameString());
      broker.withEnv(VersionUtil.VERSION_OVERRIDE_ENV_NAME, currentVersion());
    } else {
      broker.setDockerImageName(
          DockerImageName.parse("camunda/camunda").withTag(version).asCanonicalNameString());
    }

    applySecondaryStorageEnv(broker, storage);

    final var semVer = SemanticVersion.parse(version);

    if (semVer.isPresent() && semVer.get().minor() < 8) {
      broker
          .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
          .withEnv("ZEEBE_BROKER_CLUSTER_PARTITIONS_COUNT", "2")
          .withEnv("ZEEBE_BROKER_CLUSTER_REPLICATIONFACTOR", "1")
          .withEnv("ZEEBE_BROKER_CLUSTER_CLUSTER_SIZE", "2")
          .withEnv("ZEEBE_BROKER_CLUSTER_NODE_ID", String.valueOf(id))
          .withEnv("ZEEBE_BROKER_CLUSTER_CLUSTER_NAME", CamundaClusterBuilder.DEFAULT_CLUSTER_NAME)
          .withEnv(
              "ZEEBE_BROKER_CLUSTER_INITIALCONTACTPOINTS",
              String.join(",", broker.getConfiguration().getCluster().getInitialContactPoints()))
          .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true")
          .withEnv("ZEEBE_BROKER_NETWORK_HOST", "0.0.0.0")
          .withEnv("ZEEBE_BROKER_NETWORK_ADVERTISEDHOST", broker.getInternalHost())
          .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "true")
          .withEnv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "false");
    }
  }

  private String currentVersion() {
    return VersionUtil.getVersion().replace("-SNAPSHOT", "");
  }

  private ProcessInstanceEvent createProcessInstance(final CamundaClient client) {
    return client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();
  }

  private void deployProcess(final CamundaClient client) {
    client
        .newDeployResourceCommand()
        .addProcessModel(PROCESS, "process.bpmn")
        .send()
        .join(10, TimeUnit.SECONDS);
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

  private CamundaClient newClient(final GatewayNode<?> gateway) {
    return CamundaClient.newClientBuilder()
        .preferRestOverGrpc(false)
        .grpcAddress(gateway.getGrpcAddress())
        .restAddress(gateway.getRestAddress())
        .build();
  }

  private void awaitSecondOldBrokerStartDelay() {
    final long delayStartNanos = System.nanoTime();
    Awaitility.await("wait before starting second old-version broker")
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                Duration.ofNanos(System.nanoTime() - delayStartNanos)
                        .compareTo(SECOND_OLD_BROKER_START_DELAY)
                    >= 0);
  }

  private void configureBroker(
      final BrokerNode<?> broker,
      final List<String> initialContactPoints,
      final Collection<CamundaVolume> volumes,
      final StorageTestCase storage) {
    final var volume =
        CamundaVolume.newVolume(
            cfg -> {
              final var labels = new HashMap<>(Objects.requireNonNull(cfg.getLabels()));
              labels.put(
                  DockerClientFactory.TESTCONTAINERS_SESSION_ID_LABEL,
                  DockerClientFactory.SESSION_ID);
              return cfg.withLabels(labels);
            });
    volumes.add(volume);

    broker
        .withCamundaData(volume)
        .withUnifiedConfig(
            cfg -> {
              cfg.getCluster().setSize(2);
              cfg.getCluster().setInitialContactPoints(initialContactPoints);
              cfg.getCluster().getMembership().setBroadcastUpdates(true);
              cfg.getCluster().getMembership().setSyncInterval(Duration.ofMillis(250));
              cfg.getCluster().getMembership().setProbeInterval(Duration.ofMillis(100));
              cfg.getCluster().getMembership().setProbeTimeout(Duration.ofSeconds(1));
              cfg.getCluster().getMembership().setFailureTimeout(Duration.ofSeconds(2));
              cfg.getCluster().getMembership().setSuspectProbes(2);
            })
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_BROADCASTUPDATES", "true")
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_SYNCINTERVAL", "250ms")
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_PROBEINTERVAL", "100ms")
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_PROBETIMEOUT", "1s")
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_FAILURETIMEOUT", "2s")
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_SUSPECTPROBES", "2")
        .withEnv("ZEEBE_BROKER_NETWORK_ADVERTISEDHOST", broker.getInternalHost())
        .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true")
        .withEnv("ZEEBE_BROKER_CLUSTER_CLUSTERNAME", CamundaClusterBuilder.DEFAULT_CLUSTER_NAME)
        .withEnv(
            "ZEEBE_BROKER_CLUSTER_INITIALCONTACTPOINTS", String.join(",", initialContactPoints))
        .withEnv("CAMUNDA_SYSTEM_UPGRADE_ENABLEVERSIONCHECK", "false")
        .withEnv("CAMUNDA_DATABASE_SCHEMAMANAGER_VERSIONCHECKRESTRICTIONENABLED", "false")
        .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
        .withCreateContainerCmdModifier(
            createContainerCmd -> createContainerCmd.withUser("1001:0"));

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
