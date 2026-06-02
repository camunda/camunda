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
import static io.camunda.zeebe.test.ClusterHelper.createProcessInstance;
import static io.camunda.zeebe.test.ClusterHelper.deployProcess;
import static io.camunda.zeebe.test.ClusterHelper.newClient;
import static io.camunda.zeebe.test.util.testcontainers.TestSearchContainers.CAMUNDA_DATABASE;
import static io.camunda.zeebe.test.util.testcontainers.TestSearchContainers.CAMUNDA_PASSWORD;
import static io.camunda.zeebe.test.util.testcontainers.TestSearchContainers.CAMUNDA_USER;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.container.cluster.BrokerNode;
import io.camunda.container.cluster.CamundaCluster;
import io.camunda.container.cluster.GatewayNode;
import io.camunda.container.volume.CamundaVolume;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

/**
 * Rolling update test for secondary storage across mixed-version upgrades.
 *
 * <p>The same scenario is parameterized across all supported secondary storage implementations
 * (RDBMS, Elasticsearch, and OpenSearch) to avoid duplicated test logic.
 */
final class SecondaryStorageRollingUpdateTest {

  private static final List<StorageTestCase> STORAGE_TEST_CASES =
      List.of(
          StorageTestCase.rdbms(), StorageTestCase.elasticsearch(), StorageTestCase.opensearch());

  static Stream<Arguments> versionAndStorageMatrix() {
    return VersionCompatibilityMatrix.useCached()
        .fromPreviousMinorToCurrent()
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

      // --- Phase 1: start both nodes on old version with staggered startup to allow for liquibase
      // bootstrapping without race conditions ---
      final CompletableFuture<Void> oldVersionStartFuture =
          CompletableFuture.runAsync(oldVersionBroker::start);
      awaitSecondOldBrokerStartDelay();
      newVersionBroker.start();
      oldVersionStartFuture.join();
      final long phase1Key;
      try (final var client = newClient(oldVersionGateway)) {
        deployProcess(client);
        phase1Key = createProcessInstance(client);

        awaitProcessInstanceInSecondaryStorage(client, phase1Key);
      }

      // --- Phase 2: upgrade ---
      newVersionBroker.stop();
      updateBroker(newVersionBroker, newNodeId, to, storage);
      newVersionBroker.start();
      oldVersionBroker.stop();

      final long phase2Key;
      try (final var phase2Client = newClient(newVersionGateway)) {
        phase2Key = createProcessInstance(phase2Client);

        awaitProcessInstanceInSecondaryStorage(phase2Client, phase1Key);
        awaitProcessInstanceInSecondaryStorage(phase2Client, phase2Key);
      }

      // --- Phase 3: old node on new schema ---
      oldVersionBroker.start();
      newVersionBroker.stop();

      try (final var phase3Client = newClient(oldVersionGateway)) {
        final long phase3Key = createProcessInstance(phase3Client);

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
    ClusterHelper.updateBroker(broker, id, version);
    applySecondaryStorageEnv(broker, storage);
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
