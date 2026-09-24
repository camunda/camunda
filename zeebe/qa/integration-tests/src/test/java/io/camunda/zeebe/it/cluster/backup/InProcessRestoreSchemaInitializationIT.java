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
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
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

/** Verifies that an in-process primary restore reruns secondary-storage schema initialization. */
@Testcontainers
@ZeebeIntegration
final class InProcessRestoreSchemaInitializationIT {

  private static final int PARTITIONS_COUNT = 2;
  private static final long BACKUP_ID = 7;
  private static final String PROCESS_ID = "restore-schema-init-process";
  private static final String JOB_TYPE = "restore-schema-init-job";
  private static final String TASK_TEMPLATE = "tasklist-task-8.8.0_template";
  private static final Duration TIMEOUT = Duration.ofSeconds(60);

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH =
      TestSearchContainers.createDefaultElasticsearchContainer();

  @TempDir private static Path tempDir;

  @Test
  void shouldRecreateDeletedIndexTemplateDuringPrimaryRestore() {
    final var backupPath = tempDir.resolve(UUID.randomUUID().toString());
    try (final var cluster =
            TestCluster.builder()
                .withBrokersCount(2)
                .withPartitionsCount(PARTITIONS_COUNT)
                .withReplicationFactor(2)
                .withEmbeddedGateway(true)
                .withBrokerConfig(
                    broker ->
                        broker
                            .withSecondaryStorageType(SecondaryStorageType.elasticsearch)
                            .withCreateSchema(true)
                            .withUnifiedConfig(
                                cfg -> {
                                  cfg.getData()
                                      .getSecondaryStorage()
                                      .getElasticsearch()
                                      .setUrl(elasticsearchUrl());
                                  final var backup = cfg.getData().getPrimaryStorage().getBackup();
                                  backup.setStore(PrimaryStorageBackup.BackupStoreType.FILESYSTEM);
                                  backup.getFilesystem().setBasePath(backupPath.toString());
                                }))
                .build()
                .start()
                .awaitCompleteTopology();
        final var client = cluster.newClientBuilder().build()) {

      final var indicesBeforeRestore = awaitNonEmptySchema();
      assertThat(templateExists(TASK_TEMPLATE)).isTrue();
      InProcessRestoreTestUtil.deployAndCreateInstancesOnEveryPartition(
          client, PROCESS_ID, JOB_TYPE, PARTITIONS_COUNT);
      cluster.brokers().values().forEach(broker -> takeSnapshot(broker));
      takeBackup(BackupActuator.of(cluster.availableGateway()), BACKUP_ID);

      final var clusterActuator =
          io.camunda.zeebe.qa.util.actuator.ClusterActuator.of(cluster.anyGateway());
      final var toRecovering = InProcessRestoreTestUtil.changeMode(client, "RECOVERING", false);
      awaitChangeCompleted(cluster, clusterActuator, toRecovering);

      deleteIndices(indicesBeforeRestore);
      deleteTemplate(TASK_TEMPLATE);
      assertThat(indices()).isEmpty();
      assertThat(templateExists(TASK_TEMPLATE)).isFalse();

      // The secondary-storage restore is intentionally not triggered. The primary restore must run
      // schema initialization before its partition pre-restore operation.
      final var restore = InProcessRestoreTestUtil.triggerRestore(client, BACKUP_ID);
      awaitChangeCompleted(cluster, clusterActuator, restore);

      assertThat(indices()).containsAll(indicesBeforeRestore);
      assertThat(templateExists(TASK_TEMPLATE)).isTrue();
    }
  }

  private static void awaitChangeCompleted(
      final TestCluster cluster,
      final io.camunda.zeebe.qa.util.actuator.ClusterActuator actuator,
      final long changeId) {
    Awaitility.await("cluster configuration change " + changeId + " completes")
        .atMost(Duration.ofMinutes(2))
        .untilAsserted(
            () ->
                ClusterActuatorAssert.assertThat(actuator)
                    .hasCompletedChanges(changeId)
                    .doesNotHavePendingChanges());
  }

  private static void takeSnapshot(final TestStandaloneBroker broker) {
    final var partitions = io.camunda.zeebe.qa.util.actuator.PartitionsActuator.of(broker);
    partitions.takeSnapshot();
    Awaitility.await("snapshot is taken on broker " + broker.nodeId())
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(partitions.query().values())
                    .allSatisfy(status -> assertThat(status.snapshotId()).isNotNull()));
  }

  private static void takeBackup(final BackupActuator actuator, final long backupId) {
    assertThat(actuator.take(backupId)).isInstanceOf(TakeBackupRuntimeResponse.class);
    Awaitility.await("backup " + backupId + " completes")
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(actuator.status(backupId))
                    .extracting(BackupInfo::getBackupId, BackupInfo::getState)
                    .containsExactly(backupId, StateCode.COMPLETED));
  }

  private static List<String> awaitNonEmptySchema() {
    Awaitility.await("the schema is created at startup")
        .atMost(TIMEOUT)
        .untilAsserted(() -> assertThat(indices()).isNotEmpty());
    return indices();
  }

  private static List<String> indices() {
    return Arrays.stream(send("GET", "/_cat/indices?h=index").split("\n"))
        .map(String::trim)
        .filter(index -> !index.isEmpty() && !index.startsWith("."))
        .sorted()
        .toList();
  }

  private static void deleteIndices(final List<String> indices) {
    if (!indices.isEmpty()) {
      send("DELETE", "/" + String.join(",", indices));
    }
  }

  private static void deleteTemplate(final String template) {
    send("DELETE", "/_index_template/" + template);
  }

  private static boolean templateExists(final String template) {
    return sendStatus("HEAD", "/_index_template/" + template, 200, 404) == 200;
  }

  private static String send(final String method, final String path) {
    return send(method, path, 200).body();
  }

  private static int sendStatus(
      final String method, final String path, final int... expectedStatuses) {
    return send(method, path, expectedStatuses).statusCode();
  }

  private static HttpResponse<String> send(
      final String method, final String path, final int... expectedStatuses) {
    try (final var httpClient = HttpClient.newHttpClient()) {
      final var response =
          httpClient.send(
              HttpRequest.newBuilder(URI.create(elasticsearchUrl() + path))
                  .method(method, HttpRequest.BodyPublishers.noBody())
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode())
          .describedAs("%s %s returned %s".formatted(method, path, response.body()))
          .isIn(java.util.Arrays.stream(expectedStatuses).boxed().toList());
      return response;
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
