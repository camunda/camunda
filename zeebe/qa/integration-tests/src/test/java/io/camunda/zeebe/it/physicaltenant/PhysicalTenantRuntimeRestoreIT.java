/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.data.Exporter;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.data.primarystorage.PrimaryStorageBackup.BackupStoreType;
import io.camunda.zeebe.it.cluster.backup.InProcessRestoreTestUtil;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestClusterBuilder;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Restoring a cluster with a physical tenant that was created through the {@code physicalTenants}
 * actuator, so that nothing in the static configuration describes it.
 *
 * <p>Two brokers, replication factor 2, two partitions per tenant, as in {@code
 * PhysicalTenantRestoreIT}: every partition has a replica on both brokers, and the restore runs on
 * both. The default tenant and the created tenant each back up to a filesystem store of their own;
 * the created tenant's store is named only in its own configuration.
 */
@Timeout(600)
final class PhysicalTenantRuntimeRestoreIT {

  private static final String DEFAULT_TENANT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
  private static final int BROKERS = 2;
  private static final int REPLICATION_FACTOR = 2;
  private static final int PARTITIONS = 2;
  private static final long DEFAULT_BACKUP_ID = 11L;
  private static final long CREATED_BACKUP_ID = 22L;
  private static final Duration TIMEOUT = Duration.ofSeconds(180);
  private static final ObjectMapper JSON = new ObjectMapper();

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(DEFAULT_TENANT, PhysicalTenantsITHelper.Storage.none(), PARTITIONS)
          .build();

  @TempDir private Path dataRoot;
  @TempDir private Path defaultBackupDirectory;
  @TempDir private Path createdBackupDirectory;

  private final String createdTenant =
      "rt" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

  @Test
  void shouldRestoreATenantCreatedAtRuntimeWithTheWholeCluster() throws Exception {
    // given
    try (final var cluster = buildCluster()) {
      final var before = backUpBothTenants(cluster);
      cluster.shutdown();
      for (final var node : nodeIds()) {
        deleteContents(dataDirectoryOf(node));
      }

      // when
      restoreOnEveryNode(
          restoreApp ->
              restoreApp
                  .withAllPhysicalTenants()
                  .withBackupId(DEFAULT_BACKUP_ID)
                  .withProperty(
                      "override." + createdTenant + ".backupId",
                      String.valueOf(CREATED_BACKUP_ID)));

      // then
      awaitReady(cluster.start());
      assertRestoredAsItWas(cluster, before);
      assertStateRestoredFromBackup(cluster, DEFAULT_TENANT);
      assertStateRestoredFromBackup(cluster, createdTenant);
    }
  }

  @Test
  void shouldRestoreOnlyATenantCreatedAtRuntime() throws Exception {
    // given
    try (final var cluster = buildCluster()) {
      final var before = backUpBothTenants(cluster);
      cluster.shutdown();

      // when
      restoreOnEveryNode(
          restoreApp ->
              restoreApp.withPhysicalTenant(createdTenant).withBackupId(CREATED_BACKUP_ID));

      // then
      awaitReady(cluster.start());
      assertRestoredAsItWas(cluster, before);
      assertStateRestoredFromBackup(cluster, createdTenant);
      assertJobsFromEveryPartition(cluster, DEFAULT_TENANT, jobTypeAfterBackup(DEFAULT_TENANT));
    }
  }

  /**
   * Creates the tenant, leaves work in both tenants before and after their backups, and returns the
   * created tenant's status as the cluster saw it.
   */
  private JsonNode backUpBothTenants(final TestCluster cluster) throws Exception {
    awaitReady(cluster.start());
    final var created = send("POST", tenantUri(broker(cluster, 1), createdTenant), configuration());
    assertThat(created.statusCode()).describedAs(created.body()).isEqualTo(202);
    final var status = awaitRolledOut(cluster);

    for (final var tenantId : List.of(DEFAULT_TENANT, createdTenant)) {
      createPendingWorkOnEveryPartition(cluster, tenantId, jobTypeBeforeBackup(tenantId));
    }
    takeBackupAndAwait(cluster, DEFAULT_TENANT, DEFAULT_BACKUP_ID);
    takeBackupAndAwait(cluster, createdTenant, CREATED_BACKUP_ID);
    for (final var tenantId : List.of(DEFAULT_TENANT, createdTenant)) {
      createPendingWorkOnEveryPartition(cluster, tenantId, jobTypeAfterBackup(tenantId));
    }
    return status;
  }

  private void assertRestoredAsItWas(final TestCluster cluster, final JsonNode before) {
    final var after = awaitRolledOut(cluster);
    assertThat(after.get("owner").asText()).isEqualTo("API");
    assertThat(after.get("version").asLong()).isEqualTo(before.get("version").asLong());
    assertThat(after.get("sha256").asText()).isEqualTo(before.get("sha256").asText());
    assertThat(after.get("incarnationId").asText()).isEqualTo(before.get("incarnationId").asText());
  }

  private JsonNode awaitRolledOut(final TestCluster cluster) {
    final JsonNode[] status = new JsonNode[1];
    Awaitility.await("%s runs on every broker".formatted(createdTenant))
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var response = send("GET", tenantUri(broker(cluster, 0), createdTenant), null);
              assertThat(response.statusCode()).describedAs(response.body()).isEqualTo(200);
              status[0] = JSON.readTree(response.body());
              assertThat(status[0].get("rolledOut").asBoolean()).isTrue();
              assertThat(status[0].get("changing").asBoolean()).isFalse();
            });
    return status[0];
  }

  private Map<String, String> configuration() {
    final Map<String, String> configuration = new HashMap<>();
    configuration.put("data.secondary-storage.type", "none");
    configuration.put("security.initialization.default-roles.admin.users[0]", "admin");
    configuration.put("data.exporters-assigned[0]", TestStandaloneBroker.RECORDING_EXPORTER_ID);
    configuration.put("cluster.partition-count", String.valueOf(PARTITIONS));
    configuration.put("data.primary-storage.backup.store", "filesystem");
    configuration.put(
        "data.primary-storage.backup.filesystem.base-path",
        createdBackupDirectory.toAbsolutePath().toString());
    return configuration;
  }

  private void createPendingWorkOnEveryPartition(
      final TestCluster cluster, final String tenantId, final String jobType) {
    Awaitility.await("deploy to %s".formatted(tenantId))
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              try (final CamundaClient client = clientFor(cluster, tenantId)) {
                InProcessRestoreTestUtil.deployAndCreateInstancesOnEveryPartition(
                    client, jobType + "-process", jobType, PARTITIONS);
              }
            });
  }

  private void assertStateRestoredFromBackup(final TestCluster cluster, final String tenantId) {
    assertJobsFromEveryPartition(cluster, tenantId, jobTypeBeforeBackup(tenantId));
    try (final CamundaClient client = clientFor(cluster, tenantId)) {
      assertThat(
              client
                  .newActivateJobsCommand()
                  .jobType(jobTypeAfterBackup(tenantId))
                  .maxJobsToActivate(2 * PARTITIONS)
                  .send()
                  .join()
                  .getJobs())
          .describedAs("jobs created after the backup of '%s'", tenantId)
          .isEmpty();
    }
  }

  private void assertJobsFromEveryPartition(
      final TestCluster cluster, final String tenantId, final String jobType) {
    Awaitility.await("jobs of %s from every partition of %s".formatted(jobType, tenantId))
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              try (final CamundaClient client = clientFor(cluster, tenantId)) {
                InProcessRestoreTestUtil.activateAndCompleteJobsFromEveryPartition(
                    client, jobType, PARTITIONS);
              }
            });
  }

  private void takeBackupAndAwait(
      final TestCluster cluster, final String tenantId, final long backupId) throws Exception {
    final var broker = broker(cluster, 0);
    final var taken =
        send(
            "POST",
            broker.actuatorUri("backupRuntime"),
            JSON.writeValueAsString(Map.of("backupId", backupId, "physicalTenant", tenantId)));
    assertThat(taken.statusCode())
        .describedAs("take backup %d of '%s': %s", backupId, tenantId, taken.body())
        .isEqualTo(202);
    Awaitility.await("backup %d of '%s' completes".formatted(backupId, tenantId))
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var status =
                  send(
                      "GET",
                      URI.create(
                          broker.actuatorUri("backupRuntime", String.valueOf(backupId))
                              + "?physicalTenant="
                              + tenantId),
                      null);
              assertThat(status.statusCode()).isEqualTo(200);
              assertThat(JSON.readTree(status.body()).path("state").asText())
                  .isEqualTo("COMPLETED");
            });
  }

  private void restoreOnEveryNode(final UnaryOperator<TestRestoreApp> arguments) {
    for (final var memberId : nodeIds()) {
      try (final var app =
          arguments.apply(
              new TestRestoreApp()
                  .withUnifiedConfig(
                      config -> {
                        config.getCluster().setNodeId(Integer.parseInt(memberId.id()));
                        config.getCluster().setSize(BROKERS);
                        config.getCluster().setReplicationFactor(REPLICATION_FACTOR);
                        config.getCluster().setPartitionCount(PARTITIONS);
                        config
                            .getData()
                            .getPrimaryStorage()
                            .setDirectory(dataDirectoryOf(memberId).toString());
                        configureBackup(config.getData().getPrimaryStorage().getBackup());
                        config.getData().getSecondaryStorage().setType(SecondaryStorageType.none);
                        // the stored configuration of the created tenant assigns this exporter, so
                        // the restore resolves it against the same root exporters as the brokers
                        config
                            .getData()
                            .getExporters()
                            .computeIfAbsent(
                                TestStandaloneBroker.RECORDING_EXPORTER_ID, id -> new Exporter())
                            .setClassName(RecordingExporter.class.getName());
                      }))) {
        assertThatNoException()
            .describedAs("restore on node %s", memberId.id())
            .isThrownBy(app::start);
      }
    }
  }

  private TestCluster buildCluster() {
    return new TestClusterBuilder()
        .withBrokersCount(BROKERS)
        .withGatewaysCount(0)
        .withEmbeddedGateway(true)
        .withPartitionsCount(PARTITIONS)
        .withReplicationFactor(REPLICATION_FACTOR)
        .withBrokerConfig(
            (memberId, broker) ->
                broker
                    .withUnauthenticatedAccess()
                    .withDataConfig(
                        data -> {
                          data.getPrimaryStorage()
                              .setDirectory(dataDirectoryOf(memberId).toString());
                          configureBackup(data.getPrimaryStorage().getBackup());
                        }))
        .build();
  }

  private void configureBackup(
      final io.camunda.configuration.data.primarystorage.PrimaryStorageBackup backup) {
    backup.setStore(BackupStoreType.FILESYSTEM);
    backup.getFilesystem().setBasePath(defaultBackupDirectory.toString());
  }

  private static TestCluster awaitReady(final TestCluster cluster) {
    return cluster.awaitCompleteTopology(BROKERS, PARTITIONS, REPLICATION_FACTOR, TIMEOUT);
  }

  private CamundaClient clientFor(final TestCluster cluster, final String tenantId) {
    return TENANTS
        .newClientBuilder(cluster.availableGateway(), tenantId)
        .preferRestOverGrpc(false)
        .build();
  }

  private static TestStandaloneBroker broker(final TestCluster cluster, final int nodeId) {
    return cluster.brokers().get(MemberId.from(String.valueOf(nodeId)));
  }

  private static URI tenantUri(final TestStandaloneBroker broker, final String tenantId) {
    return broker.actuatorUri("physicalTenants", tenantId);
  }

  private static HttpResponse<String> send(final String method, final URI uri, final Object body) {
    try (final var http = HttpClient.newHttpClient()) {
      final var request =
          HttpRequest.newBuilder(uri)
              .header("Content-Type", "application/json")
              .method(
                  method,
                  body == null
                      ? BodyPublishers.noBody()
                      : BodyPublishers.ofString(
                          body instanceof final String text ? text : JSON.writeValueAsString(body)))
              .build();
      return http.send(request, BodyHandlers.ofString());
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String jobTypeBeforeBackup(final String tenantId) {
    return tenantId + "-before-backup";
  }

  private static String jobTypeAfterBackup(final String tenantId) {
    return tenantId + "-after-backup";
  }

  private static List<MemberId> nodeIds() {
    return IntStream.range(0, BROKERS).mapToObj(id -> MemberId.from(String.valueOf(id))).toList();
  }

  private Path dataDirectoryOf(final MemberId memberId) {
    return dataRoot.resolve("node-" + memberId.id());
  }

  private static void deleteContents(final Path root) throws IOException {
    if (Files.exists(root)) {
      try (final Stream<Path> paths = Files.walk(root)) {
        paths
            .sorted(Comparator.reverseOrder())
            .forEach(
                path -> {
                  try {
                    Files.delete(path);
                  } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                  }
                });
      }
    }
    Files.createDirectories(root);
  }
}
