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
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.camunda.zeebe.util.SemanticVersion;
import io.camunda.zeebe.util.VersionUtil;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

/**
 * Rolling update test for secondary storage (RDBMS/Postgres) across a mixed-version cluster.
 *
 * <p>This test validates that process instance data remains visible in secondary storage when:
 *
 * <ul>
 *   <li>The cluster starts on {@code current - 1}
 *   <li>One node is upgraded to {@code current}
 *   <li>Older nodes still write against the migrated schema
 *   <li>Process instances created before, during, and after the upgrade are all present
 * </ul>
 *
 * <p>The cluster uses 2 broker nodes with 2 partitions, each broker owning one partition
 * (replication factor = 1). This lets each phase run with a single active broker while keeping the
 * configuration of a 2-node cluster.
 *
 * <p>Test phases:
 *
 * <ol>
 *   <li><strong>Phase 1 (old only)</strong>: Both brokers on {@code current - 1}. Deploy a process,
 *       start instances, verify they appear in secondary storage.
 *   <li><strong>Phase 2 (partial upgrade)</strong>: Stop both brokers. Restart broker 0 with {@code
 *       current}. The new-version broker migrates the RDBMS schema and creates more instances.
 *       Verify that instances from phase 1 and phase 2 are both visible.
 *   <li><strong>Phase 3 (old on new schema)</strong>: Stop broker 0. Restart broker 1 with {@code
 *       current - 1}. The old-version broker must start without errors against the migrated schema
 *       and create more instances. Verify all instances from all phases are visible.
 * </ol>
 */
final class SecondaryStorageRollingUpdateTest {

  private static final Network NETWORK = Network.newNetwork();

  /**
   * Network alias used to refer to the PostgreSQL container from within the Docker network shared
   * by the cluster brokers.
   */
  private static final String POSTGRES_NETWORK_ALIAS = "postgres";

  private static final String POSTGRES_JDBC_URL =
      "jdbc:postgresql://"
          + POSTGRES_NETWORK_ALIAS
          + ":5432/"
          + TestSearchContainers.CAMUNDA_DATABASE;

  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

  private static List<Arguments> cachedVersionMatrix;

  @SuppressWarnings("resource")
  private final JdbcDatabaseContainer<?> postgres =
      TestSearchContainers.createDefaultPostgresContainer()
          .withNetwork(NETWORK)
          .withNetworkAliases(POSTGRES_NETWORK_ALIAS);

  /**
   * Two-broker cluster: 2 partitions, RF=1 so each broker independently owns one partition.
   *
   * <p>This topology allows phases 2 and 3 to run with a single active broker while still having
   * two distinct broker nodes participate in phase 1.
   */
  private final CamundaCluster cluster =
      CamundaCluster.builder()
          .withNetwork(NETWORK)
          .withEmbeddedGateway(true)
          .withBrokersCount(1)
          .withPartitionsCount(1)
          .withReplicationFactor(1)
          .withNodeConfig(
              node ->
                  node.withUnifiedConfig(
                          cfg -> {
                            cfg.getSystem().getUpgrade().setEnableVersionCheck(false);
                            cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.rdbms);
                            cfg.getData()
                                .getSecondaryStorage()
                                .getRdbms()
                                .setUrl(POSTGRES_JDBC_URL);
                            cfg.getData()
                                .getSecondaryStorage()
                                .getRdbms()
                                .setUsername(TestSearchContainers.CAMUNDA_USER);
                            cfg.getData()
                                .getSecondaryStorage()
                                .getRdbms()
                                .setPassword(TestSearchContainers.CAMUNDA_PASSWORD);
                          })
                      .withEnv(UNPROTECTED_API_ENV_VAR, "true")
                      .withEnv(AUTHORIZATION_CHECKS_ENV_VAR, "false"))
          .build();

  @SuppressWarnings("unused")
  @RegisterExtension
  private final ContainerLogsDumper logsPrinter = new ContainerLogsDumper(cluster::getNodes);

  private final Collection<CamundaVolume> volumes = new LinkedList<>();

  static Stream<Arguments> versionMatrix() {
    if (cachedVersionMatrix == null) {
      cachedVersionMatrix = VersionCompatibilityMatrix.auto().toList();
    }
    return cachedVersionMatrix.stream();
  }

  @BeforeEach
  void setup() {
    // PostgreSQL must be running before any broker starts so that the RDBMS exporter can connect.
    postgres.start();

    final var initialContactPoints =
        cluster.getBrokers().values().stream().map(BrokerNode::getInternalClusterAddress).toList();
    cluster.getBrokers().values().forEach(broker -> configureBroker(broker, initialContactPoints));
  }

  @AfterEach
  void tearDown() {
    cluster.stop();
    postgres.stop();
    CloseHelper.closeAll(volumes);
    volumes.clear();
  }

  /**
   * Verifies that all process instances created in all phases are present in secondary storage.
   *
   * <p>The test follows a three-phase scenario:
   *
   * <ol>
   *   <li>Phase 1: both cluster nodes run on the old version and export to RDBMS.
   *   <li>Phase 2: only broker 0 runs on the new version; the RDBMS schema is migrated and data
   *       from phase 1 remains accessible.
   *   <li>Phase 3: only broker 1 runs on the old version against the migrated schema; no errors are
   *       expected and all historical data remains visible.
   * </ol>
   */
  @ParameterizedTest(name = "from {0} to {1}", allowZeroInvocations = true)
  @MethodSource("versionMatrix")
  void shouldPreserveProcessInstancesInSecondaryStorageDuringRollingUpdate(
      final String from, final String to) {

    final BrokerNode<?> broker = cluster.getBrokers().get(0);
    final GatewayNode<?> gateway = cluster.getGateways().get("0");

    // given: cluster starts on old version
    updateBroker(broker, 1, from);
    cluster.start();

    // --- Phase 1: old version only ---
    // Deploy a process and start process instance.
    final long phase1Key;
    try (final var client = newClient(gateway)) {
      deployProcess(client);

      // Create two instances to exercise both partitions (round-robin routing).
      phase1Key =
          Awaitility.await("phase 1 first process instance creation")
              .atMost(Duration.ofSeconds(30))
              .pollInterval(Duration.ofMillis(100))
              .ignoreExceptions()
              .until(() -> createProcessInstance(client), Objects::nonNull)
              .getProcessInstanceKey();

      // Verify phase 1 instances are visible in secondary storage before moving on.
      awaitProcessInstanceInSecondaryStorage(client, phase1Key);
    }

    // --- Phase 2: upgrade ---
    // Stop node, then restart with the new version.
    // After restart broker migrates the RDBMS schema.
    broker.stop();
    updateBroker(broker, 0, to);
    broker.start();

    final long phase2Key;
    try (final var phase2Client = newClient(gateway)) {
      phase2Key =
          Awaitility.await("phase 2 process instance creation")
              .atMost(Duration.ofSeconds(60))
              .pollInterval(Duration.ofMillis(100))
              .ignoreExceptions()
              .until(() -> createProcessInstance(phase2Client), Objects::nonNull)
              .getProcessInstanceKey();

      // Both phase 1 instance and the new phase 2 instance
      // must be visible in secondary storage.
      awaitProcessInstanceInSecondaryStorage(phase2Client, phase1Key);
      awaitProcessInstanceInSecondaryStorage(phase2Client, phase2Key);
    }

    // --- Phase 3: old node on new schema ---
    // Stop the upgraded broker 0. Restart broker 1 on the old version against the
    // already-migrated schema. This must succeed without errors.
    broker.stop();
    updateBroker(broker, 1, from);
    broker.start();

    try (final var phase3Client = newClient(gateway)) {
      final long phase3Key =
          Awaitility.await("phase 3 process instance creation")
              .atMost(Duration.ofSeconds(60))
              .pollInterval(Duration.ofMillis(100))
              .ignoreExceptions()
              .until(() -> createProcessInstance(phase3Client), Objects::nonNull)
              .getProcessInstanceKey();

      // All instances from all phases must be visible in secondary storage.
      awaitProcessInstanceInSecondaryStorage(phase3Client, phase1Key);
      awaitProcessInstanceInSecondaryStorage(phase3Client, phase2Key);
      awaitProcessInstanceInSecondaryStorage(phase3Client, phase3Key);
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private void updateBroker(final BrokerNode<?> broker, final int id, final String version) {
    if ("CURRENT".equals(version)) {
      broker.setDockerImageName(
          ZeebeTestContainerDefaults.defaultTestImage().asCanonicalNameString());
      broker.withEnv(VersionUtil.VERSION_OVERRIDE_ENV_NAME, currentVersion());
    } else {
      broker.setDockerImageName(
          DockerImageName.parse("camunda/camunda").withTag(version).asCanonicalNameString());
    }

    final var semVer = SemanticVersion.parse(version);

    // For versions < 8.8 the unified configuration format is not supported.
    // Pass the required settings via explicit environment variables instead.
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
    // Strip the SNAPSHOT suffix so that the version check does not reject a pre-release upgrade.
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

  /**
   * Waits until the given process instance is visible in secondary storage via the REST search API.
   *
   * <p>The search endpoint queries secondary storage (RDBMS) rather than primary storage, so
   * asserting here proves that the RDBMS exporter has written the record.
   */
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

  /**
   * Creates a {@link CamundaClient} configured for both gRPC commands and REST queries against the
   * given gateway.
   */
  private CamundaClient newClient(final GatewayNode<?> gateway) {
    return CamundaClient.newClientBuilder()
        .preferRestOverGrpc(false)
        .grpcAddress(gateway.getGrpcAddress())
        .restAddress(gateway.getRestAddress())
        .build();
  }

  private void configureBroker(
      final BrokerNode<?> broker, final List<String> initialContactPoints) {
    final var volume =
        CamundaVolume.newVolume(
            cfg -> {
              // Workaround for
              // https://github.com/camunda-community-hub/zeebe-test-container/issues/656
              final var labels = new HashMap<>(cfg.getLabels());
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
              cfg.getCluster().getMembership().setBroadcastUpdates(true);
              cfg.getCluster().getMembership().setSyncInterval(Duration.ofMillis(250));
              cfg.getCluster().getMembership().setProbeInterval(Duration.ofMillis(100));
              cfg.getCluster().getMembership().setProbeTimeout(Duration.ofSeconds(1));
              cfg.getCluster().getMembership().setFailureTimeout(Duration.ofSeconds(2));
              cfg.getCluster().getMembership().setSuspectProbes(2);
            })
        // Pass membership settings via env vars for backward compatibility with older versions.
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
        // Pass RDBMS configuration via env vars as a backward-compatibility fallback for
        // older versions that might not read the same unified-config structure.
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", "rdbms")
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_RDBMS_URL", POSTGRES_JDBC_URL)
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_RDBMS_USERNAME", TestSearchContainers.CAMUNDA_USER)
        .withEnv(
            "CAMUNDA_DATA_SECONDARYSTORAGE_RDBMS_PASSWORD", TestSearchContainers.CAMUNDA_PASSWORD)
        .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
        // user/group workaround to allow smooth update from Zeebe 8.3 to 8.4 (user changed from
        // 1000 to 1001) and group 0 keeps data-volume ownership compatible.
        // TODO remove after 8.4 release
        .withCreateContainerCmdModifier(
            createContainerCmd -> createContainerCmd.withUser("1001:0"));
  }
}
