/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.StateCode;
import io.camunda.management.backups.TakeBackupRuntimeResponse;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * An in-process restore must leave the secondary storage carrying this version's schema, because a
 * restore is the one moment where the search engine is expected to have been restored out of band
 * and may be missing indices the running version needs.
 *
 * <p>What makes this test discriminating is the order of its steps. The indices are dropped
 * <em>after</em> the cluster is already recovering, so nothing else can put them back: the
 * schema-initialization task has long since succeeded at startup and returned, and while the tenant
 * is recovering that task is deferred rather than retried - deliberately, since recreating indices
 * underneath an Elasticsearch snapshot restore would break it. The partitions are inactive too, so
 * no exporter writes. The restore's own pre-restore step is therefore the only thing left that can
 * recreate them, and if it stopped doing so this test would fail rather than pass by accident.
 *
 * <p>Restoring the Elasticsearch data itself is out of scope: that is the operator's step, done
 * before the primary storage restore is triggered. This asserts only what Camunda owns - that the
 * schema is put back.
 */
@Testcontainers
@ZeebeIntegration
final class InProcessRestoreSchemaInitializationIT {

  private static final int PARTITIONS_COUNT = 1;
  private static final long BACKUP_ID = 7;
  private static final String PROCESS_ID = "restore-schema-init-process";
  private static final String JOB_TYPE = "restore-schema-init-job";
  private static final Duration TIMEOUT = Duration.ofSeconds(60);

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH =
      TestSearchContainers.createDefaultElasticsearchContainer();

  @TempDir private static Path tempDir;

  @Test
  void shouldRecreateSearchEngineSchemaWhileRestoring() {
    final var backupPath = tempDir.resolve(UUID.randomUUID().toString());
    try (final var cluster =
            TestCluster.builder()
                .withBrokersCount(1)
                .withPartitionsCount(PARTITIONS_COUNT)
                .withReplicationFactor(1)
                .withEmbeddedGateway(true)
                .withBrokerConfig(
                    broker ->
                        broker
                            .withSecondaryStorageType(SecondaryStorageType.elasticsearch)
                            // TestStandaloneBroker disables schema creation by default; this test
                            // is about the schema, so it needs the real one created at startup
                            .withCreateSchema(true)
                            .withUnifiedConfig(
                                cfg -> {
                                  cfg.getData()
                                      .getSecondaryStorage()
                                      .getElasticsearch()
                                      .setUrl(elasticsearchUrl());
                                  final var backup = cfg.getData().getPrimaryStorage().getBackup();
                                  backup.setStore(PrimaryStorageBackup.BackupStoreType.FILESYSTEM);
                                  backup
                                      .getFilesystem()
                                      .setBasePath(backupPath.toAbsolutePath().toString());
                                }))
                .build()
                .start()
                .awaitCompleteTopology();
        final var client = cluster.newClientBuilder().build()) {

      // given -- a cluster whose schema was created at startup, with something to back up
      final var indicesBeforeRestore = awaitNonEmptySchema();
      InProcessRestoreTestUtil.deployAndCreateInstancesOnEveryPartition(
          client, PROCESS_ID, JOB_TYPE, PARTITIONS_COUNT);
      takeSnapshot(cluster);
      // the runtime backup actuator, not the cluster-wide v2 endpoint: a restore reads the
      // primary storage backup, and the cluster-wide one additionally wants a search engine
      // snapshot repository this test deliberately does not configure
      takeBackup(BackupActuator.of(cluster.availableGateway()), BACKUP_ID);

      // and -- the cluster is recovering, which is when an operator restores the search engine
      final var toRecovering = InProcessRestoreTestUtil.changeMode(client, "RECOVERING", false);
      awaitChangeCompleted(cluster, toRecovering);

      // and -- the search engine comes back without the schema, as one restored from a snapshot
      // taken before these indices existed would. Dropped only now that the cluster is recovering,
      // so neither the schema-initialization task nor an exporter can put them back.
      deleteIndices(indicesBeforeRestore);
      assertThat(indices()).isEmpty();

      // when
      final var restore = InProcessRestoreTestUtil.triggerRestore(client, BACKUP_ID);

      // then -- the restore completes, and the schema it needed is there again
      awaitChangeCompleted(cluster, restore);
      assertThat(indices())
          .describedAs("the restore recreated every index the schema declares")
          .containsAll(indicesBeforeRestore);
    }
  }

  private static void awaitChangeCompleted(final TestCluster cluster, final long changeId) {
    final var actuator = ClusterActuator.of(cluster.anyGateway());
    Awaitility.await("cluster configuration change %d completes".formatted(changeId))
        .atMost(Duration.ofMinutes(2))
        .untilAsserted(
            () ->
                ClusterActuatorAssert.assertThat(actuator)
                    .hasCompletedChanges(changeId)
                    .doesNotHavePendingChanges());
  }

  private static void takeBackup(final BackupActuator actuator, final long backupId) {
    assertThat(actuator.take(backupId)).isInstanceOf(TakeBackupRuntimeResponse.class);
    Awaitility.await("backup %d completes".formatted(backupId))
        .atMost(TIMEOUT)
        .ignoreExceptions() // 404 NOT_FOUND until the backup is registered
        .untilAsserted(
            () ->
                assertThat(actuator.status(backupId))
                    .extracting(BackupInfo::getBackupId, BackupInfo::getState)
                    .containsExactly(backupId, StateCode.COMPLETED));
  }

  private static void takeSnapshot(final TestCluster cluster) {
    cluster
        .brokers()
        .values()
        .forEach(
            broker -> {
              final var partitions = PartitionsActuator.of(broker);
              partitions.takeSnapshot();
              Awaitility.await("snapshot is taken on broker " + broker.nodeId())
                  .atMost(TIMEOUT)
                  .untilAsserted(
                      () ->
                          assertThat(partitions.query().values())
                              .allSatisfy(status -> assertThat(status.snapshotId()).isNotNull()));
            });
  }

  /**
   * The schema is applied in the background, so the set of indices is only stable once it has
   * settled - capturing it too early would compare the restore against a partial schema.
   */
  private static List<String> awaitNonEmptySchema() {
    Awaitility.await("the schema is created at startup")
        .atMost(TIMEOUT)
        .untilAsserted(() -> assertThat(indices()).isNotEmpty());
    return indices();
  }

  /** Every index this cluster owns; Elasticsearch's own dot-prefixed indices are not ours. */
  private static List<String> indices() {
    return Arrays.stream(send("GET", "/_cat/indices?h=index").split("\n"))
        .map(String::trim)
        .filter(index -> !index.isEmpty() && !index.startsWith("."))
        .sorted()
        .toList();
  }

  private static void deleteIndices(final List<String> indices) {
    if (indices.isEmpty()) {
      return;
    }
    send("DELETE", "/" + String.join(",", indices));
  }

  private static String send(final String method, final String path) {
    try (final var httpClient = HttpClient.newHttpClient()) {
      final var request =
          HttpRequest.newBuilder(URI.create(elasticsearchUrl() + path))
              .method(method, HttpRequest.BodyPublishers.noBody())
              .build();
      final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode())
          .describedAs("%s %s returned %s".formatted(method, path, response.body()))
          .isBetween(200, 299);
      return response.body();
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to call Elasticsearch at " + path, e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while calling Elasticsearch at " + path, e);
    }
  }

  private static String elasticsearchUrl() {
    return "http://" + ELASTICSEARCH.getHttpHostAddress();
  }
}
